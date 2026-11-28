package com.aitronics.assistant

import org.junit.Assert.*
import org.junit.Test

class BomAnalysisTest {
    @Test fun quotedSupplierRowsAggregateOnlyCompatiblePartsAndUnits() {
        val report = BomAnalysis.analyze("\uFEFFPart Number,Qty,UOM,Description\r\nR1,1.25,m,\"wire, red\"\r\nR1,2.75,m,\"line\none\"\r\nR1,2,each,test\nr1,0.5,m,test")
        assertTrue(report.contains("4 item rows · 3 part/unit groups"))
        assertTrue(report.contains("R1  ·  4 m"))
        assertTrue(report.contains("R1  ·  2 each"))
        assertTrue(report.contains("r1  ·  0.5 m"))
    }
    @Test fun invalidBomsFailWithoutReportingPartialTotals() {
        for (csv in listOf("part,qty\nR1,2\nR2,-1", "part,qty\nR1,1e5", "MPN,part,qty\nA,B,1", "part,qty\n\"unclosed,2", "part,qty\nR1,1,extra", "part,qty")) {
            assertTrue(runCatching { BomAnalysis.analyze(csv) }.isFailure)
        }
    }
}
