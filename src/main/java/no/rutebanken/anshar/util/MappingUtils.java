package no.rutebanken.anshar.util;

public class MappingUtils {
    /**
     * @param dateyyyyMMdd  date in format yyyyMMdd
     * @param timeHHmmss    time in format HHmmss
     * @param lineNumber    line number
     * @param directionName A (= allée) or R (= retour)
     * @param ineoStopId    INEO message stop id
     * @param datasetId    dataset
     * @return {dateyyyyMMdd},{timeHHmmss},{lineNumber},{directionName},{ineoStopId},{datasetId}
     */
    public static String buildIneoVJKey(String dateyyyyMMdd, String timeHHmmss, String lineNumber,
                                        String directionName, String ineoStopId, String datasetId) {
        return String.format("%s,%s,%s,%s,%s,%s",
                dateyyyyMMdd,
                timeHHmmss,
                StringUtils.removeLeadingZeros(lineNumber),
                directionName,
                ineoStopId,
                datasetId);
    }
}
