package de.berlios.vch.parser.rtl;

import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.osgi.service.log.LogService;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.WebPageTitleComparator;

@Component
@Provides
public class RtlParser implements IWebParser {
    public static final String CHARSET = "UTF-8";

    public static final String ID = RtlParser.class.getName();

    private static final String BASE_URI = "http://mobilbackend.rtl.de";
    private static final String SITEMAP_URI = BASE_URI + "/xml-api/video/sitemap.xml";
    public static Map<String, String> HTTP_HEADERS = new HashMap<String, String>();
    static {
        HTTP_HEADERS.put("user-agent", "RTL-VideoApp Android");
    }

    @Requires
    private LogService logger;

    @Override
    public IOverviewPage getRoot() throws Exception {
        OverviewPage page = new OverviewPage();
        page.setParser(ID);
        page.setTitle(getTitle());
        page.setUri(new URI("vchpage://localhost/" + getId()));

        String xml = HttpUtils.get(SITEMAP_URI, HTTP_HEADERS, CHARSET);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource source = new InputSource(new StringReader(xml));
        Document doc = builder.parse(source);
        NodeList entries = doc.getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Node entry = entries.item(i);
            IWebPage parsedPage = parseEntry(entry);
            if (parsedPage != null && parsedPage.getUri() != null) {
                page.getPages().add(parsedPage);
            }
        }

        Collections.sort(page.getPages(), new WebPageTitleComparator());
        return page;
    }

    private IWebPage parseEntry(Node entry) throws URISyntaxException {
        if ("naviLink".equals(entry.getAttributes().getNamedItem("type").getNodeValue())) {
            NodeList childs = entry.getChildNodes();
            IOverviewPage overview = new OverviewPage();
            for (int j = 0; j < childs.getLength(); j++) {
                Node child = childs.item(j);
                if ("link".equals(child.getNodeName())) {
                    overview.setUri(new URI(child.getTextContent().trim()));
                } else if ("text".equals(child.getNodeName())) {
                    overview.setTitle(child.getTextContent().trim());
                }
            }
            overview.setParser(getId());
            return overview;
        }
        if ("videoteaser".equals(entry.getAttributes().getNamedItem("type").getNodeValue())) {
            NodeList childs = entry.getChildNodes();
            IVideoPage video = new VideoPage();
            video.setParser(getId());
            for (int j = 0; j < childs.getLength(); j++) {
                Node child = childs.item(j);
                if ("videoId".equals(child.getNodeName())) {
                    video.setUri(new URI("rtl://video/" + child.getTextContent().trim()));
                } else if ("headline1".equals(child.getNodeName())) {
                    video.setTitle(child.getTextContent().trim());
                } else if ("image".equals(child.getNodeName())) {
                    video.setThumbnail(new URI(child.getTextContent().trim()));
                } else if ("longtext".equals(child.getNodeName())) {
                    video.setDescription(child.getTextContent().trim());
                } else if ("videoUrl".equals(child.getNodeName())) {
                    video.setVideoUri(new URI(child.getTextContent().trim()));
                }
            }
            return video;
        }

        return null;
    }

    @Override
    public String getTitle() {
        return "RTL";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        logger.log(LogService.LOG_INFO, "Parsing page " + page.getUri());

        if (page instanceof IOverviewPage) {
            IOverviewPage opage = (IOverviewPage) page;
            String xml = HttpUtils.get(page.getUri().toString(), HTTP_HEADERS, CHARSET);
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(new StringReader(xml));
            Document doc = builder.parse(source);
            NodeList entries = doc.getElementsByTagName("entry");
            for (int i = 0; i < entries.getLength(); i++) {
                Node entry = entries.item(i);
                IWebPage parsedPage = parseEntry(entry);
                if (parsedPage != null) {
                    opage.getPages().add(parsedPage);
                }
            }
        }

        return page;
    }

    @Override
    public String getId() {
        return ID;
    }
}