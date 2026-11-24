package com.stockscope.charts;

import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import com.stockscope.services.ComparisonService;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Draws comparison charts with JFreeChart and saves them as PNG files.
 *
 * Every chart follows the same steps:
 *   1. Put the numbers into a "dataset" (JFreeChart's table of values).
 *   2. Ask ChartFactory to build a basic chart from it.
 *   3. Restyle it (colors, fonts, gridlines) so it's clean and readable.
 *   4. Save it as a PNG.
 */
public class ChartGenerator {

    // Charts are drawn into an image in memory - no window is ever opened. "Headless" mode
    // tells Java not to look for a screen, which matters on servers and stops an icon
    // popping up in the macOS Dock. A static block runs once, when the class first loads.
    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 600;
    private static final int TREND_YEARS = 5;
    private static final String SOURCE = "Source: SEC EDGAR 10-K filings";

    // Colors: a light surface, gray "ink" for text, and series colors chosen to stay
    // distinguishable for colorblind readers (checked with a palette validator).
    private static final Color SURFACE = new Color(0xFCFCFB);
    private static final Color INK = new Color(0x0B0B0B);
    private static final Color SECONDARY_INK = new Color(0x52514E);
    private static final Color GRIDLINE = new Color(0xE1E0D9);
    private static final Color AXIS_LINE = new Color(0xC3C2B7);
    private static final Color[] SERIES_COLORS = {
            new Color(0x2A78D6), // blue
            new Color(0xEB6834), // orange
            new Color(0x1BAF7A), // aqua
            new Color(0xEDA100), // yellow
    };

    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    private static final Font SUBTITLE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Font AXIS_LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Font TICK_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font VALUE_LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);

    private final Path outputDir;

    public ChartGenerator(Path outputDir) {
        this.outputDir = outputDir;
    }

    /** Creates all four charts and returns the paths of the saved PNG files. */
    public List<Path> generateAll(List<CompanyFinancials> companies) throws IOException {
        Files.createDirectories(outputDir);
        // e.g. "AAPL-MSFT-NVDA", so different comparisons don't overwrite each other
        String prefix = companies.stream()
                .map(company -> company.company().ticker())
                .collect(Collectors.joining("-"));

        List<Path> saved = new ArrayList<>();
        saved.add(save(revenueChart(companies), prefix + "_revenue.png"));
        saved.add(save(netIncomeChart(companies), prefix + "_net_income.png"));
        saved.add(save(netProfitMarginChart(companies), prefix + "_net_profit_margin.png"));
        saved.add(save(revenueTrendChart(companies), prefix + "_revenue_trend.png"));
        return saved;
    }

    private Path save(JFreeChart chart, String fileName) throws IOException {
        Path file = outputDir.resolve(fileName);
        ChartUtils.saveChartAsPNG(file.toFile(), chart, WIDTH, HEIGHT);
        return file;
    }

    // ----- Chart 1 and 2: revenue and net income (bar charts) -----

    static JFreeChart revenueChart(List<CompanyFinancials> companies) {
        return moneyBarChart("Revenue Comparison", "Revenue", companies, FinancialData::revenue);
    }

    static JFreeChart netIncomeChart(List<CompanyFinancials> companies) {
        return moneyBarChart("Net Income Comparison", "Net income", companies, FinancialData::netIncome);
    }

    /** A bar chart of one dollar amount from each company's latest fiscal year. */
    private static JFreeChart moneyBarChart(String title, String metricName, List<CompanyFinancials> companies,
                                            Function<FinancialData, Long> metric) {
        List<Long> values = companies.stream().map(company -> metric.apply(company.latestYear())).toList();
        MoneyUnit unit = MoneyUnit.choose(values);

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (CompanyFinancials company : companies) {
            Long value = metric.apply(company.latestYear());
            dataset.addValue(unit.scale(value), metricName, barLabel(company, value));
        }
        return barChart(title, unit.axisLabel(metricName), dataset, unit.valueFormat(), unit.axisFormat());
    }

    // ----- Chart 3: net profit margin (bar chart) -----

    static JFreeChart netProfitMarginChart(List<CompanyFinancials> companies) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (CompanyFinancials company : companies) {
            Double margin = ComparisonService.latestRatios(company).netProfitMargin();
            dataset.addValue(margin, "Net profit margin", barLabel(company, margin));
        }
        return barChart("Net Profit Margin Comparison", "Net profit margin (% of revenue)", dataset,
                usFormat("0.0'%'"), usFormat("0'%'"));
    }

    /** Shared setup for the three bar charts. */
    private static JFreeChart barChart(String title, String valueAxisLabel, DefaultCategoryDataset dataset,
                                       NumberFormat valueFormat, NumberFormat axisFormat) {
        JFreeChart chart = ChartFactory.createBarChart(title, null, valueAxisLabel, dataset);
        chart.removeLegend(); // only one data series - the title already says what's plotted
        applyStyle(chart, "Latest fiscal year for each company  ·  " + SOURCE);

        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter()); // flat color instead of a gradient
        renderer.setShadowVisible(false);
        renderer.setSeriesPaint(0, SERIES_COLORS[0]);
        renderer.setMaximumBarWidth(0.06); // thin bars, with space between them

        // Print each bar's value at its tip, so readers don't have to estimate from the axis.
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", valueFormat));
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(VALUE_LABEL_FONT);
        renderer.setDefaultItemLabelPaint(INK);

        NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
        valueAxis.setNumberFormatOverride(axisFormat);
        valueAxis.setUpperMargin(0.12); // leave room above the tallest bar for its label
        valueAxis.setLowerMargin(0.12); // ...and below a negative bar (a loss) for its label
        return chart;
    }

    /** "AAPL (FY2026)" - or "MCD (FY2025, N/A)" when the value is missing, so a gap isn't a mystery. */
    private static String barLabel(CompanyFinancials company, Object value) {
        String label = company.company().ticker() + " (FY" + company.latestYear().fiscalYear();
        return label + (value == null ? ", N/A)" : ")");
    }

    // ----- Chart 4: five-year revenue trend (line chart) -----

    static JFreeChart revenueTrendChart(List<CompanyFinancials> companies) {
        List<Long> allRevenue = new ArrayList<>();
        for (CompanyFinancials company : companies) {
            for (FinancialData year : company.recentYears(TREND_YEARS)) {
                allRevenue.add(year.revenue());
            }
        }
        MoneyUnit unit = MoneyUnit.choose(allRevenue);

        JFreeChart chart = ChartFactory.createLineChart("Five-Year Revenue Trend", "Fiscal year",
                unit.axisLabel("Revenue"), trendDataset(companies, unit));
        applyStyle(chart, "Up to five most recent fiscal years per company  ·  " + SOURCE);

        CategoryPlot plot = chart.getCategoryPlot();
        ((NumberAxis) plot.getRangeAxis()).setNumberFormatOverride(unit.axisFormat());

        LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
        renderer.setDefaultShapesVisible(true);
        for (int i = 0; i < companies.size(); i++) {
            // Each company keeps the same color slot as its position in the comparison.
            renderer.setSeriesPaint(i, SERIES_COLORS[i % SERIES_COLORS.length]);
            renderer.setSeriesStroke(i, new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            renderer.setSeriesShape(i, new Ellipse2D.Double(-4.5, -4.5, 9, 9));
        }
        // A thin ring in the background color around each dot keeps it readable where lines cross.
        renderer.setUseOutlinePaint(true);
        renderer.setDrawOutlines(true);
        renderer.setDefaultOutlinePaint(SURFACE);
        renderer.setDefaultOutlineStroke(new BasicStroke(2f));

        // Label only the last point of each line (its latest revenue), to avoid clutter.
        renderer.setDefaultItemLabelGenerator(new LastPointLabelGenerator(unit.valueFormat()));
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(VALUE_LABEL_FONT);
        renderer.setDefaultItemLabelPaint(INK);
        return chart;
    }

    /**
     * Builds the line chart's data: one row (line) per company, one column per fiscal year.
     * Companies' fiscal years don't always line up (Apple's latest may be 2026 while
     * Amazon's is 2025), so the x-axis covers every year any company has, in order, and a
     * company simply has no point for a year it doesn't cover.
     */
    static DefaultCategoryDataset trendDataset(List<CompanyFinancials> companies, MoneyUnit unit) {
        TreeSet<Integer> allYears = new TreeSet<>(); // a TreeSet keeps the years sorted
        for (CompanyFinancials company : companies) {
            for (FinancialData year : company.recentYears(TREND_YEARS)) {
                allYears.add(year.fiscalYear());
            }
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        // JFreeChart orders the x-axis by when each column is first added, so the outer
        // loop goes through the years in order.
        for (int fiscalYear : allYears) {
            for (CompanyFinancials company : companies) {
                Long revenue = null;
                for (FinancialData year : company.recentYears(TREND_YEARS)) {
                    if (year.fiscalYear() == fiscalYear) {
                        revenue = year.revenue();
                    }
                }
                dataset.addValue(unit.scale(revenue), seriesName(company), "FY" + fiscalYear);
            }
        }
        return dataset;
    }

    /** The legend name for a company's line - flagged if it has no line to draw at all. */
    private static String seriesName(CompanyFinancials company) {
        boolean hasRevenue = company.recentYears(TREND_YEARS).stream().anyMatch(year -> year.revenue() != null);
        String ticker = company.company().ticker();
        return hasRevenue ? ticker : ticker + " (no revenue data)";
    }

    /** Shows a value label only on each line's final data point. */
    private static class LastPointLabelGenerator extends StandardCategoryItemLabelGenerator {

        LastPointLabelGenerator(NumberFormat format) {
            super("{2}", format);
        }

        @Override
        public String generateLabel(CategoryDataset dataset, int row, int column) {
            for (int later = column + 1; later < dataset.getColumnCount(); later++) {
                if (dataset.getValue(row, later) != null) {
                    return null; // this line has a later point, so don't label this one
                }
            }
            return super.generateLabel(dataset, row, column);
        }
    }

    // ----- Shared styling -----

    /** Replaces JFreeChart's default look with a clean one: light background, hairline grid. */
    private static void applyStyle(JFreeChart chart, String subtitle) {
        chart.setBackgroundPaint(SURFACE);
        chart.setPadding(new RectangleInsets(12, 16, 12, 28));
        chart.getTitle().setFont(TITLE_FONT);
        chart.getTitle().setPaint(INK);

        TextTitle subtitleText = new TextTitle(subtitle, SUBTITLE_FONT);
        subtitleText.setPaint(SECONDARY_INK);
        subtitleText.setMargin(4, 0, 12, 0);
        chart.addSubtitle(subtitleText);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(SURFACE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(GRIDLINE);
        plot.setRangeGridlineStroke(new BasicStroke(1f)); // solid - JFreeChart's default is dashed
        plot.setRangeZeroBaselineVisible(true);           // a clear 0 line, so losses read as negative
        plot.setRangeZeroBaselinePaint(AXIS_LINE);

        CategoryAxis xAxis = plot.getDomainAxis();
        xAxis.setTickLabelFont(TICK_FONT);
        xAxis.setTickLabelPaint(SECONDARY_INK);
        xAxis.setLabelFont(AXIS_LABEL_FONT);
        xAxis.setLabelPaint(SECONDARY_INK);
        xAxis.setAxisLinePaint(AXIS_LINE);
        xAxis.setTickMarksVisible(false);

        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setTickLabelFont(TICK_FONT);
        yAxis.setTickLabelPaint(SECONDARY_INK);
        yAxis.setLabelFont(AXIS_LABEL_FONT);
        yAxis.setLabelPaint(SECONDARY_INK);
        yAxis.setAxisLineVisible(false);
        yAxis.setTickMarksVisible(false);
        // JFreeChart fits in as many gridlines as the labels allow. Extra space around each
        // label makes it choose wider steps (e.g. $100B instead of $50B), so the grid is calmer.
        yAxis.setTickLabelInsets(new RectangleInsets(12, 4, 12, 6));

        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(AXIS_LABEL_FONT);
            legend.setItemPaint(INK);
            legend.setBackgroundPaint(SURFACE);
            legend.setFrame(BlockBorder.NONE);
        }
    }

    /** A number format that always uses "." for decimals, whatever the computer's language. */
    private static NumberFormat usFormat(String pattern) {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
    }

    /**
     * Charts show dollars in billions ("$478.8B") - or millions for smaller companies -
     * so the axis isn't full of twelve-digit numbers.
     */
    record MoneyUnit(double divisor, String suffix, String name) {

        static MoneyUnit choose(List<Long> values) {
            long largest = values.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Math::abs)
                    .max()
                    .orElse(0);
            return largest >= 1_000_000_000L
                    ? new MoneyUnit(1e9, "B", "billions")
                    : new MoneyUnit(1e6, "M", "millions");
        }

        /** Converts dollars to this unit; keeps null as null (no data = no bar/point). */
        Double scale(Long dollars) {
            return dollars == null ? null : dollars / divisor;
        }

        String axisLabel(String metricName) {
            return metricName + " (" + name + " of US dollars)";
        }

        NumberFormat valueFormat() {
            return usFormat("'$'#,##0.0'" + suffix + "'"); // e.g. $478.8B
        }

        NumberFormat axisFormat() {
            return usFormat("'$'#,##0'" + suffix + "'");   // e.g. $400B
        }
    }
}
