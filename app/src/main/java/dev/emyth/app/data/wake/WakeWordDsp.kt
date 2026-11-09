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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 语音唤醒的 DSP 核心：MFCC + CMVN + 余弦相似度 DTW 匹配。
 * 参考 Operit 的 PersonalWakeFeatureExtractor / PersonalWakeListener 思路：
 * 用余弦相似度（对音量不敏感）+ CMVN（消除音量差异），替代对音量敏感的欧氏距离。
 */
object WakeWordDsp {

    const val SAMPLE_RATE = 16000
    private const val FRAME_LEN = 400   // 25ms
    private const val FRAME_SHIFT = 160 // 10ms
    private const val FFT_SIZE = 512
    private const val NUM_MEL = 40
    private const val NUM_MFCC = 13
    private const val PRE_EMPHASIS = 0.97f
    private const val F_MIN = 20f
    private const val F_MAX = 4000f
    private const val EPS = 1e-10f

    /** 梅尔滤波器组（20~4000Hz，滤掉高低频噪声） */
    private val melFilters: Array<FloatArray> by lazy { computeMelFilters() }

    /** 汉明窗 */
    private val hamming: FloatArray by lazy {
        FloatArray(FRAME_LEN) { i -> (0.54f - 0.46f * cos(2.0 * PI * i / (FRAME_LEN - 1))).toFloat() }
    }

    /** 从 PCM 采样提取 MFCC 帧序列（含 CMVN 归一化）。 */
    fun extract(samples: ShortArray): List<DoubleArray> {
        if (samples.size < FRAME_LEN) return emptyList()
        // 归一化 PCM 到 [-1,1] 并去均值
        val norm = FloatArray(samples.size)
        var mean = 0f
        for (i in samples.indices) { mean += samples[i] / 32768.0f }
        mean /= samples.size
        for (i in samples.indices) { norm[i] = samples[i] / 32768.0f - mean }

        val frames = ArrayList<DoubleArray>()
        var start = 0
        while (start + FRAME_LEN <= norm.size) {
            frames.add(mfccOf(norm, start))
            start += FRAME_SHIFT
        }
        // CMVN：对每维跨时间做均值方差归一化
        cmvn(frames)
        return frames
    }

    private fun cmvn(frames: ArrayList<DoubleArray>) {
        if (frames.isEmpty()) return
        val t = frames.size
        val d = frames[0].size
        for (k in 0 until d) {
            var mean = 0.0
            for (i in 0 until t) mean += frames[i][k]
            mean /= t
            var varSum = 0.0
            for (i in 0 until t) { val diff = frames[i][k] - mean; varSum += diff * diff }
            val std = sqrt(max(EPS.toDouble(), varSum / t))
            for (i in 0 until t) frames[i][k] = (frames[i][k] - mean) / std
        }
    }

    /** 计算一帧的 MFCC 系数。 */
    private fun mfccOf(samples: FloatArray, start: Int): DoubleArray {
        val frame = FloatArray(FRAME_LEN)
        var prev = 0f
        for (i in 0 until FRAME_LEN) {
            val x = samples[start + i]
            val emph = x - PRE_EMPHASIS * prev
            prev = x
            frame[i] = emph * hamming[i]
        }
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        for (i in 0 until FRAME_LEN) re[i] = frame[i].toDouble()
        fft(re, im)
        val power = DoubleArray(FFT_SIZE / 2 + 1)
        for (i in power.indices) {
            power[i] = (re[i] * re[i] + im[i] * im[i]) / FFT_SIZE
        }
        val melEnergies = FloatArray(NUM_MEL)
        for (m in 0 until NUM_MEL) {
            val f = melFilters[m]
            var e = 0f
            for (i in power.indices) {
                e += (power[i] * f[i]).toFloat()
            }
            melEnergies[m] = if (e < EPS) EPS else e
        }
        // ln 能量
        for (m in 0 until NUM_MEL) melEnergies[m] = ln(melEnergies[m])
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

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
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

    private fun computeMelFilters(): Array<FloatArray> {
        val maxFreq = SAMPLE_RATE / 2.0
        val minMel = hzToMel(F_MIN.toDouble())
        val maxMel = hzToMel(F_MAX.toDouble())
        val melPoints = DoubleArray(NUM_MEL + 2)
        for (i in melPoints.indices) {
            melPoints[i] = melToHz(minMel + (maxMel - minMel) * i / (NUM_MEL + 1))
        }
        val filters = Array(NUM_MEL) { FloatArray(FFT_SIZE / 2 + 1) }
        for (m in 1..NUM_MEL) {
            val left = melPoints[m - 1]
            val center = melPoints[m]
            val right = melPoints[m + 1]
            for (i in filters[m - 1].indices) {
                val freq = i.toDouble() * SAMPLE_RATE / FFT_SIZE
                filters[m - 1][i] = when {
                    freq <= left || freq >= right -> 0f
                    freq <= center -> ((freq - left) / (center - left)).toFloat()
                    else -> ((right - freq) / (right - center)).toFloat()
                }
            }
        }
        return filters
    }

    private fun hzToMel(hz: Double) = 2595.0 * ln(1.0 + hz / 700.0) / ln(10.0)
    private fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

    private fun sin(x: Double) = kotlin.math.sin(x)

    /** 余弦距离：1 - 余弦相似度，范围 [0,2]，对音量（模长）不敏感。 */
    private fun cosineDistance(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        if (denom < EPS) return 1.0
        val sim = dot / denom
        return 1.0 - sim
    }

    /** 两段 MFCC 序列的 DTW 相似度（余弦距离 + band 约束），返回 0~1，越高越像。 */
    fun dtwSimilarity(a: List<DoubleArray>, b: List<DoubleArray>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val n = a.size
        val m = b.size
        val bandW = max(4, abs(n - m))
        val dp = Array(n + 1) { DoubleArray(m + 1) { Double.MAX_VALUE } }
        dp[0][0] = 0.0
        for (i in 1..n) {
            val jStart = max(1, i - bandW)
            val jEnd = min(m, i + bandW)
            for (j in jStart..jEnd) {
                val cost = cosineDistance(a[i - 1], b[j - 1])
                val bestPrev = min(dp[i - 1][j], min(dp[i][j - 1], dp[i - 1][j - 1]))
                dp[i][j] = cost + bestPrev
            }
        }
        val norm = max(1.0, (n + m).toDouble())
        val avgCost = dp[n][m] / norm
        val sim = 1.0 - (avgCost / 2.0)
        return sim.coerceIn(0.0, 1.0)
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
