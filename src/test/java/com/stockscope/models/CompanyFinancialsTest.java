package com.stockscope.models;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanyFinancialsTest {

    private static final Company COMPANY = new Company("SMPL", 1, "Sample Corp");

    @Test
    void companyWithNoYearsIsRejected() {
        // latestYear() would crash on an empty list, so this is stopped at creation instead.
        assertThrows(IllegalArgumentException.class, () -> new CompanyFinancials(COMPANY, List.of()));
    }

    @Test
    void yearsCannotBeChangedAfterCreation() {
        List<FinancialData> years = new ArrayList<>(List.of(year(2023)));
        CompanyFinancials financials = new CompanyFinancials(COMPANY, years);

        years.add(year(2024)); // changing the original list...
        assertEquals(1, financials.years().size()); // ...doesn't affect the copy

        assertThrows(UnsupportedOperationException.class, () -> financials.years().add(year(2025)));
    }

    private static FinancialData year(int fiscalYear) {
        return new FinancialData(fiscalYear, LocalDate.of(fiscalYear, 12, 31),
                1L, 1L, 1L, 1L, 1L, 1L, 1L, false);
    }
}
