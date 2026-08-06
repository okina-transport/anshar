package no.rutebanken.anshar.translation;

public interface SiriEntityTranslator<T> {

    void handleTranslations(T entity, String datasetId);

}
