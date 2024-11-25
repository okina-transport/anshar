package no.rutebanken.anshar.metrics;

import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.UnknownSnapshot;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

public class JmxMetricsConverter {

    private static final String NEW_LINE = "\n";

    private JmxMetricsConverter() {
        throw new IllegalStateException("Utility class");
    }

    public static String convertMetricSnapshotToPrometheusString(MetricSnapshot metric) {
        StringBuilder sb = new StringBuilder();
        String metricName = metric.getMetadata().getName();
        if (metric instanceof UnknownSnapshot) {
            readUnknownSnapshot(metric, sb, metricName);

        }
        if (metric instanceof GaugeSnapshot) {
            readGaugeSnapshot(metric, sb, metricName);
        }

        if (metric instanceof CounterSnapshot) {
            readCounterSnapshot(metric, sb, metricName);
        }

        return sb.toString();
    }

    private static void readUnknownSnapshot(MetricSnapshot metric, StringBuilder sb, String metricName) {
        List<UnknownSnapshot.UnknownDataPointSnapshot> labels = (List<UnknownSnapshot.UnknownDataPointSnapshot>) metric.getDataPoints();
        if (CollectionUtils.isNotEmpty(labels)) {
            for (UnknownSnapshot.UnknownDataPointSnapshot data : labels) {
                double value = data.getValue();

                String formated = data.getLabels().toString();

                sb.append(metricName)
                        .append(formated)
                        .append(" ")
                        .append(value)
                        .append(NEW_LINE);
            }
        }
    }

    private static void readGaugeSnapshot(MetricSnapshot metric, StringBuilder sb, String metricName) {
        List<GaugeSnapshot.GaugeDataPointSnapshot> labels = (List<GaugeSnapshot.GaugeDataPointSnapshot>) metric.getDataPoints();
        if (CollectionUtils.isNotEmpty(labels)) {
            for (GaugeSnapshot.GaugeDataPointSnapshot data : labels) {
                double value = data.getValue();

                String formated = data.getLabels().toString();

                sb.append(metricName)
                        .append(formated)
                        .append(" ")
                        .append(value)
                        .append(NEW_LINE);
            }
        }
    }

    private static void readCounterSnapshot(MetricSnapshot metric, StringBuilder sb, String metricName) {
        List<CounterSnapshot.CounterDataPointSnapshot> labels = (List<CounterSnapshot.CounterDataPointSnapshot>) metric.getDataPoints();
        if (CollectionUtils.isNotEmpty(labels)) {
            for (CounterSnapshot.CounterDataPointSnapshot data : labels) {
                double value = data.getValue();

                String formated = data.getLabels().toString();

                sb.append(metricName)
                        .append(formated)
                        .append(" ")
                        .append(value)
                        .append(NEW_LINE);
            }
        }
    }
}
