package no.rutebanken.anshar.routes.dataformat;

import org.apache.camel.Exchange;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.spi.annotations.Dataformat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Dataformat("jaxb")
public class ThreadSafeDataFormat extends JaxbDataFormat {

    private static final Object LOCK = new Object();

    public ThreadSafeDataFormat(String contextPath) {
        super(contextPath);
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws IOException {
        synchronized (LOCK) {
            super.marshal(exchange, graph, stream);
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws IOException {
        synchronized (LOCK) {
            return super.unmarshal(exchange, stream);
        }
    }
}
