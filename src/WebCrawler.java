import sun.plugin.javascript.navig4.Link;

import java.util.Set;

/**
 * Created by TStone on 12/16/16.
 */



public class WebCrawler {


    private void initCrawlerWithSeeds(String[] seeds) {
        for(int i = 0; i < seeds.length; i++) {
            LinkQueue.addUnVisitedUrl(seeds[i]);
        }
    }

    public void crawling(String[] seeds) {
        LinkFilter filter = new LinkFilter() {
            @Override
            public boolean accept(String url) {
                if(url.startsWith("https://wwww.baidu.com"))
                    return true;
                else
                    return false;
            }
        };
        initCrawlerWithSeeds(seeds);

        while(!LinkQueue.isUnVisitedUrlEmpty() && LinkQueue.getVisitedUrlNum() <= 1000) {
            String visitUrl = (String)LinkQueue.unVisitedUrlDequeue();
            if(visitUrl == null)
                continue;
            DownloadFile downLoader = new DownloadFile();
            downLoader.downloadFile(visitUrl);
            LinkQueue.addVisitedUrl(visitUrl);
            Set<String> links = HtmlParserTool.extracLinks(visitUrl, filter);
            for(String link: links) {
                LinkQueue.addUnVisitedUrl(link);
            }
        }
    }

    public static void main(String args[]) {

//        System.out.println("xxx");
//
//        DownloadFile downloadFile = new DownloadFile();
//        downloadFile.downloadFile("https://www.baidu.com");
        WebCrawler webCrawler = new WebCrawler();
        webCrawler.crawling(new String[] {"https://www.baidu.com"});
    }
}
