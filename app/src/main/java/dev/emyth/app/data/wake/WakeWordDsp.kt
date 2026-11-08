/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.wake

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 语音唤醒的 DSP 核心：MFCC 特征提取 + DTW 匹配。
 * 纯本地离线，16kHz 单声道 PCM。参考 Operit 的 PersonalWakeFeatureExtractor 思路。
 */
object WakeWordDsp {

    const val SAMPLE_RATE = 16000
    private const val FRAME_LEN = 400   // 25ms
    private const val FRAME_SHIFT = 160 // 10ms
    private const val FFT_SIZE = 512
    private const val NUM_MEL = 26
    private const val NUM_MFCC = 13
    private const val PRE_EMPHASIS = 0.97

    /** 梅尔滤波器组（三角滤波器，预计算） */
    private val melFilters: Array<DoubleArray> by lazy { computeMelFilters() }

    /** 汉明窗 */
    private val hamming: DoubleArray by lazy {
        DoubleArray(FRAME_LEN) { i -> 0.54 - 0.46 * cos(2.0 * PI * i / (FRAME_LEN - 1)) }
    }

    /** 从 PCM 采样提取 MFCC 帧序列。 */
    fun extract(samples: ShortArray): List<DoubleArray> {
        if (samples.size < FRAME_LEN) return emptyList()
        val frames = ArrayList<DoubleArray>()
        var start = 0
        while (start + FRAME_LEN <= samples.size) {
            frames.add(mfccOf(samples, start))
            start += FRAME_SHIFT
        }
        return frames
    }

    /** 计算一帧的 MFCC 系数。 */
    private fun mfccOf(samples: ShortArray, start: Int): DoubleArray {
        // 预加重 + 加窗
        val frame = DoubleArray(FRAME_LEN)
        var prev = 0.0
        for (i in 0 until FRAME_LEN) {
            val x = samples[start + i].toDouble()
            val emph = x - PRE_EMPHASIS * prev
            prev = x
            frame[i] = emph * hamming[i]
        }
        // FFT
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        for (i in 0 until FRAME_LEN) re[i] = frame[i]
        fft(re, im)
        // 功率谱
        val power = DoubleArray(FFT_SIZE / 2 + 1)
        for (i in power.indices) {
            power[i] = (re[i] * re[i] + im[i] * im[i]) / FFT_SIZE
        }
        // 梅尔滤波器组能量
        val melEnergies = DoubleArray(NUM_MEL)
        for (m in 0 until NUM_MEL) {
            val f = melFilters[m]
            var e = 0.0
            for (i in power.indices) {
                e += power[i] * f[i]
            }
            melEnergies[m] = if (e < 1e-10) 1e-10 else e
        }
        // log
        for (m in 0 until NUM_MEL) melEnergies[m] = log10(melEnergies[m])
        // DCT -> MFCC
        val mfcc = DoubleArray(NUM_MFCC)
        for (k in 0 until NUM_MFCC) {
            var sum = 0.0
            for (m in 0 until NUM_MEL) {
                sum += melEnergies[m] * cos(PI * k * (m + 0.5) / NUM_MEL)
            }
            mfcc[k] = sum
        }
        return mfcc
    }

    /** 迭代 radix-2 FFT（Cooley-Tukey）。 */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nxtRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nxtRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** 预计算梅尔三角滤波器组。 */
    private fun computeMelFilters(): Array<DoubleArray> {
        val maxFreq = SAMPLE_RATE / 2.0
        val minMel = hzToMel(0.0)
        val maxMel = hzToMel(maxFreq)
        val melPoints = DoubleArray(NUM_MEL + 2)
        for (i in melPoints.indices) {
            melPoints[i] = melToHz(minMel + (maxMel - minMel) * i / (NUM_MEL + 1))
        }
        val filters = Array(NUM_MEL) { DoubleArray(FFT_SIZE / 2 + 1) }
        for (m in 1..NUM_MEL) {
            val left = melPoints[m - 1]
            val center = melPoints[m]
            val right = melPoints[m + 1]
            for (i in filters[m - 1].indices) {
                val freq = i.toDouble() * SAMPLE_RATE / FFT_SIZE
                filters[m - 1][i] = when {
                    freq <= left || freq >= right -> 0.0
                    freq <= center -> (freq - left) / (center - left)
                    else -> (right - freq) / (right - center)
                }
            }
        }
        return filters
    }

    private fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

    private fun sin(x: Double) = kotlin.math.sin(x)

    /** 两段 MFCC 序列的 DTW 距离。 */
    fun dtwDistance(a: List<DoubleArray>, b: List<DoubleArray>): Double {
        if (a.isEmpty() || b.isEmpty()) return Double.MAX_VALUE
        val n = a.size
        val m = b.size
        val dtw = Array(n + 1) { DoubleArray(m + 1) { Double.MAX_VALUE } }
        dtw[0][0] = 0.0
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = euclidean(a[i - 1], b[j - 1])
                dtw[i][j] = cost + minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
            }
        }
        return dtw[n][m] / (n + m).toDouble()
    }

    private fun euclidean(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum)
    }

    /** 把 MFCC 序列存成模板文件（二进制）。 */
    fun saveTemplate(file: File, frames: List<DoubleArray>) {
        DataOutputStream(file.outputStream()).use { out ->
            out.writeInt(frames.size)
            for (f in frames) {
                out.writeInt(f.size)
                for (v in f) out.writeDouble(v)
            }
        }
    }

    /** 从模板文件读取 MFCC 序列。 */
    fun loadTemplate(file: File): List<DoubleArray> {
        if (!file.exists()) return emptyList()
        DataInputStream(file.inputStream()).use { inp ->
            val n = inp.readInt()
            val frames = ArrayList<DoubleArray>(n)
            for (i in 0 until n) {
                val len = inp.readInt()
                val f = DoubleArray(len)
                for (j in 0 until len) f[j] = inp.readDouble()
                frames.add(f)
            }
            return frames
        }
    }
}
