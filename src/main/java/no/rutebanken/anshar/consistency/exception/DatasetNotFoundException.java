package no.rutebanken.anshar.consistency.exception;

public class DatasetNotFoundException extends RuntimeException {

    private final String dataset;
    
    public DatasetNotFoundException(String dataset) {
        this.dataset = dataset;
    }

    @Override
    public String getMessage() {
        return String.format("Dataset '%s' not found", dataset);
    }
}
