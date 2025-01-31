package com.muwire.gui

import com.muwire.core.util.DataUtil

class UISettings {

    String lnf
    boolean showMonitor
    String font
    boolean autoFontSize
    int fontSize
    boolean clearCancelledDownloads
    boolean clearFinishedDownloads
    boolean excludeLocalResult
    boolean showSearchHashes
    boolean closeWarning
    boolean certificateWarning
    boolean exitOnClose
    boolean clearUploads
    boolean storeSearchHistory
    boolean groupByFile
    Set<String> searchHistory
    Set<String> openTabs
    
    UISettings(Properties props) {
        lnf = props.getProperty("lnf", "system")
        showMonitor = Boolean.parseBoolean(props.getProperty("showMonitor", "false"))
        font = props.getProperty("font",null)
        clearCancelledDownloads = Boolean.parseBoolean(props.getProperty("clearCancelledDownloads","true"))
        clearFinishedDownloads = Boolean.parseBoolean(props.getProperty("clearFinishedDownloads","false"))
        excludeLocalResult = Boolean.parseBoolean(props.getProperty("excludeLocalResult","true"))
        showSearchHashes = Boolean.parseBoolean(props.getProperty("showSearchHashes","true"))
        autoFontSize = Boolean.parseBoolean(props.getProperty("autoFontSize","false"))
        fontSize = Integer.parseInt(props.getProperty("fontSize","12"))
        closeWarning = Boolean.parseBoolean(props.getProperty("closeWarning","true"))
        certificateWarning = Boolean.parseBoolean(props.getProperty("certificateWarning","true"))
        exitOnClose = Boolean.parseBoolean(props.getProperty("exitOnClose","false"))
        clearUploads = Boolean.parseBoolean(props.getProperty("clearUploads","false"))
        storeSearchHistory = Boolean.parseBoolean(props.getProperty("storeSearchHistory","true"))
        groupByFile = Boolean.parseBoolean(props.getProperty("groupByFile","false"))
        
        searchHistory = DataUtil.readEncodedSet(props, "searchHistory")
        openTabs = DataUtil.readEncodedSet(props, "openTabs")
    }

    void write(OutputStream out) throws IOException {
        Properties props = new Properties()
        props.setProperty("lnf", lnf)
        props.setProperty("showMonitor", String.valueOf(showMonitor))
        props.setProperty("clearCancelledDownloads", String.valueOf(clearCancelledDownloads))
        props.setProperty("clearFinishedDownloads", String.valueOf(clearFinishedDownloads))
        props.setProperty("excludeLocalResult", String.valueOf(excludeLocalResult))
        props.setProperty("showSearchHashes", String.valueOf(showSearchHashes))
        props.setProperty("autoFontSize", String.valueOf(autoFontSize))
        props.setProperty("fontSize", String.valueOf(fontSize))
        props.setProperty("closeWarning", String.valueOf(closeWarning))
        props.setProperty("certificateWarning", String.valueOf(certificateWarning))
        props.setProperty("exitOnClose", String.valueOf(exitOnClose))
        props.setProperty("clearUploads", String.valueOf(clearUploads))
        props.setProperty("storeSearchHistory", String.valueOf(storeSearchHistory))
        props.setProperty("groupByFile", String.valueOf(groupByFile))
        if (font != null)
            props.setProperty("font", font)

        DataUtil.writeEncodedSet(searchHistory, "searchHistory", props)
        DataUtil.writeEncodedSet(openTabs, "openTabs", props)

        props.store(out, "UI Properties")
    }
}
