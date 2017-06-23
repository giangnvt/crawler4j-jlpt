/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.giangnvt.j2e.grammar.crawler;

import com.google.common.io.Files;

import org.apache.http.Header;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;

/**
 * @author Yasser Ganjisaffar
 */
public class JlptGrammarListCrawler extends WebCrawler {

    private static final Pattern IMAGE_EXTENSIONS = Pattern.compile(".*\\.(bmp|gif|jpg|png)$");
    private static final String ITEM_URL_PATTERN = "http://japanesetest4you\\.com/flashcard/(learn-jlpt-n\\d-grammar)-(.*)";
    private static final String ITEM_URL_PATTERN2 = "http://japanesetest4you\\.com/flashcard/learn-japanese-grammar-.*";
    private static final String ITEM_URL_PATTERN3 = "http://japanesetest4you\\.com/flashcard/jlpt-n5-grammar-.*";
    private static Map<String, Integer> subFolderFileIndexMap;
    {
        subFolderFileIndexMap = new HashMap<>();
    }
    private static Pattern pattern1 = Pattern.compile(ITEM_URL_PATTERN);
    private static Pattern pattern2 = Pattern.compile(ITEM_URL_PATTERN2);
    private static Pattern pattern3 = Pattern.compile(ITEM_URL_PATTERN3);

    /**
     * You should implement this function to specify whether the given url
     * should be crawled or not (based on your crawling logic).
     */
    @Override
    public boolean shouldVisit(Page referringPage, WebURL url) {
        String href = url.getURL().toLowerCase();
        // Ignore the url if it has an extension that matches our defined set of image extensions.
        if (IMAGE_EXTENSIONS.matcher(href).matches()) {
            return false;
        }

        // Only accept the url if it is in the "www.ics.uci.edu" domain and protocol is "http".
        return href.matches(ITEM_URL_PATTERN)
                || href.matches(ITEM_URL_PATTERN2)
                        || href.matches(ITEM_URL_PATTERN3);
    }

    /**
     * This function is called when a page is fetched and ready to be processed
     * by your program.
     */
    @Override
    public void visit(Page page) {
        int docid = page.getWebURL().getDocid();
        String url = page.getWebURL().getURL();
        String domain = page.getWebURL().getDomain();
        String path = page.getWebURL().getPath();
        String subDomain = page.getWebURL().getSubDomain();
        String parentUrl = page.getWebURL().getParentUrl();
        String anchor = page.getWebURL().getAnchor();

        logger.debug("Docid: {}", docid);
        logger.info("URL: {}", url);
        logger.debug("Domain: '{}'", domain);
        logger.debug("Sub-domain: '{}'", subDomain);
        logger.debug("Path: '{}'", path);
        logger.debug("Parent page: {}", parentUrl);
        logger.debug("Anchor text: {}", anchor);

        if (page.getParseData() instanceof HtmlParseData) {
            HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
            String text = htmlParseData.getText();
            String html = htmlParseData.getHtml();
            
            boolean matched = false;
            java.util.regex.Matcher matcher = pattern1.matcher(url);
            matched = matcher.matches();
            
            if (!matched) {
                matcher = pattern2.matcher(url);
                matched = matcher.matches();
            }

            if (!matched) {
                matcher = pattern3.matcher(url);
                matched = matcher.matches();
            }
            
            System.out.println("=================================");
            if (matched) {
                try {
                    String rootFolder = getMyController().getConfig().getCrawlStorageFolder();
                    if (!rootFolder.endsWith("\\") && !rootFolder.endsWith("/")) {
                        rootFolder += "/";
                    }
                    rootFolder += "grammar/";
                    
                    //String subDir = matcher.group(1) + "/";
                    String subDir = parentUrl;
                    if (subDir.endsWith("/"))
                        subDir = subDir.substring(0, subDir.length() - 1);
                    if (subDir.contains("/"))
                        subDir = subDir.substring(subDir.lastIndexOf("/") + 1);
                    
//                    String originalFileName = matcher.group(1);
//                    System.out.println("Original fileName: " + originalFileName);
                    String originalFileName = anchor;
                    
                    int fileIdx = 1;
                    if (!subFolderFileIndexMap.containsKey(subDir)) {
                        new File (rootFolder + subDir).mkdirs();
                    } else {
                        fileIdx = subFolderFileIndexMap.get(subDir) + 1;
                    }
                    subFolderFileIndexMap.put(subDir, fileIdx);
                    
                    String fileName = java.net.URLDecoder.decode(originalFileName, "UTF-8");
                    fileName = fileName.replaceAll("/", "");
                    fileName = fileName.replaceAll("\\\\", "");
                    fileName = String.format("%03d_%s%s", fileIdx, fileName, ".html");
                    System.out.println("Decoded fileName: " + fileName);
                    
                    File newDir = new File(rootFolder + subDir + "/" + fileName.replaceAll(".html", ""));
                    newDir.mkdirs();
                    Files.write(page.getContentData(), new File(newDir, fileName));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("=================================");
            
            List<WebURL> links = htmlParseData.getOutgoingUrls();

            logger.debug("Text length: {}", text.length());
            logger.debug("Html length: {}", html.length());
            logger.debug("Number of outgoing links: {}", links.size());
        }

        Header[] responseHeaders = page.getFetchResponseHeaders();
        if (responseHeaders != null) {
            logger.debug("Response headers:");
            for (Header header : responseHeaders) {
                logger.debug("\t{}: {}", header.getName(), header.getValue());
            }
        }

        logger.debug("=============");
    }
}
