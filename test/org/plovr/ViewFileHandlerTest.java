package org.plovr;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.assertEquals;

import java.io.OutputStream;
import java.io.File;
import java.net.URI;

public class ViewFileHandlerTest extends HandlerTest {
  @Test
  public void testViewFileHandler() throws Exception {
    ViewFileHandler handler = createHandler();
    Config config = ConfigParser.parseFile(new File("testdata/modules/plovr-config.js"));
    URI uri = new URI("/view?name=app_init.js");
    HttpExchange ex = createExchange(uri);
    QueryData qdata = QueryData.createFromUri(uri);

    handler.doGet(ex, qdata, config);
    Mockito.verify(ex).sendResponseHeaders(Mockito.eq(200), Mockito.anyInt());
  }

  private ViewFileHandler createHandler() {
    return new ViewFileHandler(createServer());
  }
}
