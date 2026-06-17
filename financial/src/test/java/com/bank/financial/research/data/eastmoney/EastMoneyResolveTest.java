package com.bank.financial.research.data.eastmoney;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bank.financial.research.data.ResearchDataSource;
import com.bank.financial.research.data.eastmoney.EastMoneyResearchDataSource.Symbol;
import org.junit.jupiter.api.Test;

/** Unit layer: ticker → exchange-qualified id resolution (pure, no network). */
class EastMoneyResolveTest {

    @Test
    void resolvesShanghaiByInference() {
        Symbol s = EastMoneyResearchDataSource.resolve("600519");
        assertEquals("600519", s.code());
        assertEquals("SH", s.exchange());
        assertEquals("600519.SH", s.secucode());
        assertEquals("sh600519", s.sinaCode());
    }

    @Test
    void resolvesShenzhenByInference() {
        Symbol s = EastMoneyResearchDataSource.resolve("000001");
        assertEquals("SZ", s.exchange());
        assertEquals("sz000001", s.sinaCode());
    }

    @Test
    void acceptsDottedAndPrefixedForms() {
        assertEquals("600519.SH", EastMoneyResearchDataSource.resolve("600519.SH").secucode());
        assertEquals("600519.SH", EastMoneyResearchDataSource.resolve("sh600519").secucode());
        assertEquals("300750.SZ", EastMoneyResearchDataSource.resolve("300750").secucode());
    }

    @Test
    void rejectsNonAShareCodes() {
        assertThrows(ResearchDataSource.DataUnavailableException.class,
                () -> EastMoneyResearchDataSource.resolve("DEMO"));
        assertThrows(ResearchDataSource.DataUnavailableException.class,
                () -> EastMoneyResearchDataSource.resolve("AAPL"));
        assertThrows(ResearchDataSource.DataUnavailableException.class,
                () -> EastMoneyResearchDataSource.resolve(""));
    }
}
