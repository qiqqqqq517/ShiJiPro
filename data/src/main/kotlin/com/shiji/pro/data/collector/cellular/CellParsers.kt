package com.shiji.pro.data.collector.cellular

import android.annotation.SuppressLint
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import com.shiji.pro.core.model.CellData
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.RadioTech

/**
 * 6 制式 CellInfo 解析器（设计稿 §4.2）。
 *
 * 每个 Parser 顶部注明：制式 → CellInfo 子类 → 字段 → 奇异值判定。
 * 所有字段经 [Sentinels] 包装：取不到一律 FieldValue.unavailable，绝不编造数值。
 * 「换算」字段（ECI / Band / 频率 / NR 频段）由 UseCase 层调用 FrequencyConverter 补齐，
 * 本解析器只输出**实测**原始字段，保持职责单一。
 */
/** GSM ARFCN/BSIC 需 API 24、WCDMA UARFCN 需 API 24；NR/TDS 类需 API 28/29。
 *  分派入口只在对应 CellInfo 子类存在时调用（该子类本身即由对应 API 引入），
 *  故 @TargetApi(29) 是准确的局部下限声明；低版本不会走到这些分支。 */
@android.annotation.TargetApi(29)
object CellParsers {

    /** 统一分派：把平台 CellInfo 解析成领域 CellData；无法识别的返回 null。 */
    @SuppressLint("NewApi")
    fun parse(info: CellInfo): CellData? = when (info) {
        is CellInfoLte -> parseLte(info)
        is CellInfoNr -> parseNr(info)
        is CellInfoGsm -> parseGsm(info)
        is CellInfoWcdma -> parseWcdma(info)
        is CellInfoTdscdma -> parseTdscdma(info)
        is CellInfoCdma -> parseCdma(info)
        else -> null
    }

    // ---- LTE：CellInfoLte → TAC/CI/PCI/EARFCN/Bands + RSRP/RSRQ/SINR/TA/CQI ----
    @SuppressLint("NewApi")
    private fun parseLte(info: CellInfoLte): CellData {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val f = mutableMapOf<String, FieldValue<*>>()
        id.tac.toField("TAC").let { f["TAC"] = it }
        id.ci.toField("CI").let { f["CI"] = it }
        id.pci.toField("PCI").let { f["PCI"] = it }
        id.earfcn.toField("EARFCN").let { f["EARFCN"] = it }
        f["Band"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bands = id.bands
            if (bands == null || bands.isEmpty()) FieldValue.unavailable<String>("getBands() 机型未上报")
            else FieldValue.measured(bands.joinToString(","))
        } else FieldValue.unavailable<String>("getBands() 需 API 30+，由 EARFCN 查表换算")
        f["Bandwidth"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            id.bandwidth.toField("Bandwidth")
        } else FieldValue.unavailable<Int>("getBandwidth() 需 API 28+")
        ss.rsrp.toField("RSRP").let { f["RSRP"] = it }
        ss.rsrq.toField("RSRQ").let { f["RSRQ"] = it }
        ss.rssnr.toField("SINR").let { f["SINR"] = it }
        f["RSSI"] = ss.rssi.toField("RSSI")
        f["TA"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ss.timingAdvance.toField("TimingAdvance")
        } else FieldValue.unavailable<Int>("getTimingAdvance() 需 API 26+")
        f["CQI"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ss.cqi.toField("CQI")
        } else FieldValue.unavailable<Int>("getCqi() 需 API 26+")
        f["Level"] = FieldValue.derived(ss.level, "平台信号等级")
        return CellData(RadioTech.LTE, info.isRegistered, f, info.isRegistered)
    }

    // ---- 5G NR：CellInfoNr → NR-PCI/NCI/TAC/NRARFCN/Bands + SS/CSI RSRP/RSRQ/SINR ----
    @Suppress("MissingPermission")
    @SuppressLint("NewApi")
    private fun parseNr(info: CellInfoNr): CellData {
        val id = info.cellIdentity as? CellIdentityNr
            ?: return CellData(RadioTech.NR, info.isRegistered,
                mapOf("NR" to FieldValue.unavailable<String>("CellInfoNr 未返回 CellIdentityNr")), info.isRegistered)
        val ss = info.cellSignalStrength as? CellSignalStrengthNr
        val f = mutableMapOf<String, FieldValue<*>>()
        id.pci.toField("NR-PCI").let { f["NR-PCI"] = it }
        id.tac.toField("TAC").let { f["TAC"] = it }
        f["NCI(NR-CGI)"] = id.nci.toLongField("NCI")
        id.nrarfcn.toField("NR-ARFCN").let { f["NR-ARFCN"] = it }
        f["Bands"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bands = id.bands
            if (bands == null || bands.isEmpty()) FieldValue.unavailable<String>("getBands() 机型未上报")
            else FieldValue.measured(bands.joinToString(","))
        } else FieldValue.unavailable<String>("getBands() 需 API 30+")
        if (ss != null) {
            f["SS-RSRP"] = ss.ssRsrp.toField("SS-RSRP")
            f["SS-RSRQ"] = ss.ssRsrq.toField("SS-RSRQ")
            f["SS-SINR"] = ss.ssSinr.toField("SS-SINR")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                f["CSI-RSRP"] = ss.csiRsrp.toField("CSI-RSRP")
                f["CSI-RSRQ"] = ss.csiRsrq.toField("CSI-RSRQ")
                f["CSI-SINR"] = ss.csiSinr.toField("CSI-SINR")
            }
        } else {
            f["SS-RSRP"] = FieldValue.unavailable<Int>("CellInfoNr 未返回信号强度")
        }
        return CellData(RadioTech.NR, info.isRegistered, f, info.isRegistered)
    }

    // ---- GSM：LAC/CI/ARFCN/BSIC + RXLEV(ASU→dBm 换算) ----
    @android.annotation.TargetApi(24)
    private fun parseGsm(info: CellInfoGsm): CellData {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val f = mutableMapOf<String, FieldValue<*>>(
            "LAC" to id.lac.toField("LAC"),
            "CI" to id.cid.toField("CI"),
            "ARFCN" to id.arfcn.toField("ARFCN"),
            "BSIC" to id.bsic.toField("BSIC"),
            "RXLEV" to ss.dbm.toField("RXLEV(dBm)"),
            "ASU" to FieldValue.derived(ss.asuLevel, "平台 ASU 等级"),
        )
        return CellData(RadioTech.GSM, info.isRegistered, f, info.isRegistered)
    }

    // ---- WCDMA：LAC/CI/UARFCN/PSC + RXLEV ----
    @android.annotation.TargetApi(24)
    private fun parseWcdma(info: CellInfoWcdma): CellData {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val f = mutableMapOf<String, FieldValue<*>>(
            "LAC" to id.lac.toField("LAC"),
            "CI" to id.cid.toField("CI"),
            "UARFCN" to id.uarfcn.toField("UARFCN"),
            "PSC" to id.psc.toField("PSC"),
            "RXLEV" to ss.dbm.toField("RXLEV(dBm)"),
        )
        return CellData(RadioTech.WCDMA, info.isRegistered, f, info.isRegistered)
    }

    // ---- TDSCDMA：API 28+；LAC/CI/UARFCN/CPID + RXLEV ----
    @SuppressLint("NewApi")
    private fun parseTdscdma(info: CellInfoTdscdma): CellData? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val f = mutableMapOf<String, FieldValue<*>>(
            "LAC" to id.lac.toField("LAC"),
            "CI" to id.cid.toField("CI"),
            "UARFCN" to id.uarfcn.toField("UARFCN"),
            "CPID" to id.cpid.toField("CPID"),
            "RXLEV" to ss.dbm.toField("RXLEV(dBm)"),
        )
        return CellData(RadioTech.TDSCDMA, info.isRegistered, f, info.isRegistered)
    }

    // ---- CDMA/EVDO：NID/BID/SID + CDMA dBm/EcIo + EVDO dBm/EcIo/SNR ----
    private fun parseCdma(info: CellInfoCdma): CellData {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val f = mutableMapOf<String, FieldValue<*>>(
            "NID" to id.networkId.toField("NID"),
            "BID" to id.basestationId.toField("BID"),
            "SID" to id.systemId.toField("SID"),
            "CDMA dBm" to ss.cdmaDbm.toField("CDMA dBm"),
            "CDMA EcIo" to ss.cdmaEcio.toField("CDMA EcIo"),
            "EVDO dBm" to ss.evdoDbm.toField("EVDO dBm"),
            "EVDO EcIo" to ss.evdoEcio.toField("EVDO EcIo"),
            "EVDO SNR" to ss.evdoSnr.toField("EVDO SNR"),
        )
        return CellData(RadioTech.CDMA, info.isRegistered, f, info.isRegistered)
    }

}
