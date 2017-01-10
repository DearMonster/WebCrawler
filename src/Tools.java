import com.sun.net.httpserver.HttpsParameters;

import com.sun.tools.internal.ws.wsdl.document.jaxws.Exception;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.*;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.EncodingUtils;
import org.apache.http.util.EntityUtils;

import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.filters.OrFilter;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;


import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Created by TStone on 12/23/16.
 */
class Queue {
    private LinkedList queue = new LinkedList();

    public void enQueue(Object t) {
        queue.add(t);
    }
    public Object deQueue() { return queue.removeFirst(); }
    public boolean isQueueEmpty() { return queue.isEmpty(); }
    public boolean contains(Object t) { return queue.contains(t); }
    public boolean empty() { return queue.isEmpty(); }
}

class LinkQueue {
    private static Set visited = new HashSet();
    private static Queue unVisited = new Queue();

    public static Queue getUnVisitedUrl() { return unVisited; }
    public static void addVisitedUrl(String url) { visited.add(url); }
    public static void removeVisitedUrl(String url) { visited.remove(url); }
    public static Object unVisitedUrlDequeue() { return unVisited.deQueue(); }
    public static void addUnVisitedUrl(String url) {
        if(url != null && !url.trim().equals("") && !visited.contains(url) && !unVisited.contains(url)) {
            unVisited.enQueue(url);
        }
    }
    public static int getVisitedUrlNum() { return visited.size(); }
    public static boolean isUnVisitedUrlEmpty() { return unVisited.empty(); }
}

class DownloadFile {

    public String getFileNameByUrl(String url, String contentType) {
        url = url.substring(7);
        if(contentType.indexOf("html") != -1) {
            url = url.replaceAll("[\\?/:*|<>\"]", "_");
            return url;
        }
        else {  // application/ pdf
            return url.replaceAll("[\\?/:*|<>\"]", "_") + "." + contentType.substring(
                    contentType.lastIndexOf("/") + 1);
        }
    }

    private void saveToLocal(byte[] data, String filePath) {
        try{
            DataOutputStream out = new DataOutputStream(new FileOutputStream(new File(filePath)));
            for(int i = 0;i < data.length; i++) {
                out.write(data[i]);
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] getEntityContent2ByteArray(HttpEntity entity) {
        // check for gzip
        Header header = entity.getContentEncoding();
        boolean isGzip = false;
        if(header != null) {
            for(HeaderElement headerElement: header.getElements()) {
                if(headerElement.getName().equalsIgnoreCase("gzip")) {
                    isGzip = true;
                }
            }
        }
        int contentLength = (int)entity.getContentLength();
        if(contentLength < 0) {
            contentLength = 4096;
        }
        ByteArrayBuffer buffer = new ByteArrayBuffer(contentLength);
        byte[] tmp = new byte[4096];
        int count;
        try{
            if(isGzip) {
                GZIPInputStream gzipInputStream = new GZIPInputStream(entity.getContent());
                while((count = gzipInputStream.read(tmp)) != -1) {
                    buffer.append(tmp, 0, count);
                }
                System.out.println("entity is Gzip coding");
            }
            else {
                InputStream inputStream = entity.getContent();
                while((count = inputStream.read(tmp)) != -1) {
                    buffer.append(tmp, 0, count);
                }
                System.out.println("entity is not Gzip coding");
            }
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
        return buffer.toByteArray();
    }

    public String downloadFile(String url) {
        String filePath = null;
        ConnectionKeepAliveStrategy strategy = new DefaultConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                return 5 * 1000;
            }
        };
        HttpRequestRetryHandler requestRetryHandler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException e, int executionCount, HttpContext httpContext) {
                if(executionCount > 3) {
                    return false;
                }
                if(e instanceof ConnectTimeoutException) {
                    return true;
                }
                return false;
            }
        };
        CloseableHttpClient httpClient = HttpClients.custom().setKeepAliveStrategy(strategy).
                setRetryHandler(requestRetryHandler).build();
        HttpGet httpGet = new HttpGet(url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(5000).build();
        httpGet.setConfig(requestConfig);
        CloseableHttpResponse closeableHttpResponse = null;
        try{
            closeableHttpResponse = httpClient.execute(httpGet);
            int statusCode = closeableHttpResponse.getStatusLine().getStatusCode();
            if(statusCode != HttpStatus.SC_OK) {
                System.err.println("Method failed: " + httpGet.getURI().toString());
                filePath = null;
            }
            filePath = "temp\\" + getFileNameByUrl(url, closeableHttpResponse.getHeaders("Content-Type")[0].getValue());
            System.out.println("filePath = " + filePath);
            saveToLocal(getEntityContent2ByteArray(closeableHttpResponse.getEntity()), filePath);
        } catch (ClientProtocolException e) {
            System.out.println("Please check your provided http address!");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(closeableHttpResponse != null) {
                try {
                    closeableHttpResponse.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            httpGet.abort();
            try {
                httpClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return filePath;
    }
}

interface LinkFilter {
    public boolean accept(String url);
}

class HtmlParserTool {
    //  get a URL on website. filter URL by 'filter'
    public static Set<String> extracLinks(String url, LinkFilter filter) {
        Set<String> links = new HashSet<String>();
        try{
            Parser parser = new Parser(url);
            parser.setEncoding("gb2312");
            NodeFilter frameFilter = new NodeFilter() {
                @Override
                public boolean accept(org.htmlparser.Node node) {
                    if(node.getText().startsWith("frame src=")) {
                        return true;
                    }
                    else {
                        return false;
                    }
                }
            };
            OrFilter linkFilter = new OrFilter(new NodeClassFilter(LinkTag.class), frameFilter);
            NodeList list = parser.extractAllNodesThatMatch(linkFilter);
            for(int i = 0; i< list.size(); i++){
                Node tag = list.elementAt(i);
                if(tag instanceof LinkTag) {  // <a>
                    LinkTag link = (LinkTag) tag;
                    String linkUrl = link.getLink();  // URL
                    if(filter.accept(linkUrl)) {
                        links.add(linkUrl);
                    }
                }
                else {   // get 'src'
                    String frame = tag.getText();
                    frame = frame.substring(frame.indexOf("src="));
                    int end = frame.indexOf(" ");
                    if(end == -1) {
                        end = frame.indexOf(">");
                    }
                    String frameUrl = frame.substring(5, end - 1);
                    if(filter.accept(frameUrl)) {
                        links.add(frameUrl);
                    }
                }

            }
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
        return links;
    }
}