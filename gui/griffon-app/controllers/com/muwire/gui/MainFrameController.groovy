package com.muwire.gui

import griffon.core.GriffonApplication
import griffon.core.artifact.GriffonController
import griffon.core.controller.ControllerAction
import griffon.core.mvc.MVCGroup
import griffon.core.mvc.MVCGroupConfiguration
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor
import net.i2p.crypto.DSAEngine
import net.i2p.data.Base64
import net.i2p.data.Signature

import java.awt.event.ActionEvent
import java.nio.charset.StandardCharsets

import javax.annotation.Nonnull
import javax.inject.Inject
import javax.swing.JTable

import com.muwire.core.Core
import com.muwire.core.Persona
import com.muwire.core.SharedFile
import com.muwire.core.SplitPattern
import com.muwire.core.download.Downloader
import com.muwire.core.download.DownloadStartedEvent
import com.muwire.core.download.UIDownloadCancelledEvent
import com.muwire.core.download.UIDownloadEvent
import com.muwire.core.download.UIDownloadPausedEvent
import com.muwire.core.download.UIDownloadResumedEvent
import com.muwire.core.filecert.UICreateCertificateEvent
import com.muwire.core.files.DirectoryUnsharedEvent
import com.muwire.core.files.FileUnsharedEvent
import com.muwire.core.files.UIPersistFilesEvent
import com.muwire.core.search.QueryEvent
import com.muwire.core.search.SearchEvent
import com.muwire.core.trust.RemoteTrustList
import com.muwire.core.trust.TrustEvent
import com.muwire.core.trust.TrustLevel
import com.muwire.core.trust.TrustSubscriptionEvent

@ArtifactProviderFor(GriffonController)
class MainFrameController {
    @Inject @Nonnull GriffonApplication application
    @MVCMember @Nonnull
    FactoryBuilderSupport builder

    @MVCMember @Nonnull
    MainFrameModel model
    @MVCMember @Nonnull
    MainFrameView view

    private volatile Core core

    @ControllerAction
    void clearSearch() {
        def searchField = builder.getVariable("search-field")
        searchField.setSelectedItem(null)
        searchField.requestFocus()
    }
    
    @ControllerAction
    void search(ActionEvent evt) {
        if (evt?.getActionCommand() == null)
            return
        def cardsPanel = builder.getVariable("cards-panel")
        cardsPanel.getLayout().show(cardsPanel, "search window")

        def searchField = builder.getVariable("search-field") 
        def search = searchField.getSelectedItem()
        searchField.model.addElement(search)
        performSearch(search)
    }
    
    private void performSearch(String search) {
        
        model.sessionRestored = true
        
        search = search.trim()
        if (search.length() == 0)
            return
        if (search.length() > 128)
            search = search.substring(0,128)
        def uuid = UUID.randomUUID()
        Map<String, Object> params = new HashMap<>()
        params["search-terms"] = search
        params["uuid"] = uuid.toString()
        params["core"] = core
        def group = mvcGroup.createMVCGroup("SearchTab", uuid.toString(), params)
        model.results[uuid.toString()] = group

        boolean hashSearch = false
        byte [] root = null
        if (search.length() == 44 && search.indexOf(" ") < 0) {
            try {
                root = Base64.decode(search)
                hashSearch = true
            } catch (Exception e) {
                // not a hash search
            }
        }

        def searchEvent
        byte [] payload
        if (hashSearch) {
            searchEvent = new SearchEvent(searchHash : root, uuid : uuid, oobInfohash: true, compressedResults : true)
            payload = root
        } else {
            // this can be improved a lot
            def replaced = search.toLowerCase().trim().replaceAll(SplitPattern.SPLIT_PATTERN, " ")
            def terms = replaced.split(" ")
            def nonEmpty = []
            terms.each { if (it.length() > 0) nonEmpty << it }
            payload = String.join(" ",nonEmpty).getBytes(StandardCharsets.UTF_8)
            searchEvent = new SearchEvent(searchTerms : nonEmpty, uuid : uuid, oobInfohash: true,
            searchComments : core.muOptions.searchComments, compressedResults : true)
        }
        boolean firstHop = core.muOptions.allowUntrusted || core.muOptions.searchExtraHop

        Signature sig = DSAEngine.getInstance().sign(payload, core.spk)

        core.eventBus.publish(new QueryEvent(searchEvent : searchEvent, firstHop : firstHop,
        replyTo: core.me.destination, receivedOn: core.me.destination,
        originator : core.me, sig : sig.data))

    }

    void search(String infoHash, String tabTitle) {
        def cardsPanel = builder.getVariable("cards-panel")
        cardsPanel.getLayout().show(cardsPanel, "search window")
        def uuid = UUID.randomUUID()
        Map<String, Object> params = new HashMap<>()
        params["search-terms"] = tabTitle
        params["uuid"] = uuid.toString()
        params["core"] = core
        def group = mvcGroup.createMVCGroup("SearchTab", uuid.toString(), params)
        model.results[uuid.toString()] = group

        byte [] infoHashBytes = Base64.decode(infoHash)
        Signature sig = DSAEngine.getInstance().sign(infoHashBytes, core.spk)
        
        def searchEvent = new SearchEvent(searchHash : Base64.decode(infoHash), uuid:uuid,
            oobInfohash: true)
        core.eventBus.publish(new QueryEvent(searchEvent : searchEvent, firstHop : true,
            replyTo: core.me.destination, receivedOn: core.me.destination,
            originator : core.me, sig : sig.data))
    }

    private int selectedDownload() {
        def downloadsTable = builder.getVariable("downloads-table")
        def selected = downloadsTable.getSelectedRow()
        def sortEvt = mvcGroup.view.lastDownloadSortEvent
        if (sortEvt != null)
            selected = downloadsTable.rowSorter.convertRowIndexToModel(selected)
        selected
    }

    @ControllerAction
    void trustPersonaFromSearch() {
        int selected = builder.getVariable("searches-table").getSelectedRow()
        if (selected < 0)
            return
        Persona p = model.searches[selected].originator
        core.eventBus.publish( new TrustEvent(persona : p, level : TrustLevel.TRUSTED) )
    }

    @ControllerAction
    void distrustPersonaFromSearch() {
        int selected = builder.getVariable("searches-table").getSelectedRow()
        if (selected < 0)
            return
        Persona p = model.searches[selected].originator
        core.eventBus.publish( new TrustEvent(persona : p, level : TrustLevel.DISTRUSTED) )
    }

    @ControllerAction
    void cancel() {
        def downloader = model.downloads[selectedDownload()].downloader
        downloader.cancel()
        model.downloadInfoHashes.remove(downloader.getInfoHash())
        core.eventBus.publish(new UIDownloadCancelledEvent(downloader : downloader))
    }

    @ControllerAction
    void resume() {
        def downloader = model.downloads[selectedDownload()].downloader
        downloader.resume()
        core.eventBus.publish(new UIDownloadResumedEvent())
    }

    @ControllerAction
    void pause() {
        def downloader = model.downloads[selectedDownload()].downloader
        downloader.pause()
        core.eventBus.publish(new UIDownloadPausedEvent())
    }

    @ControllerAction
    void clear() {
        def toRemove = []
        model.downloads.each {
             if (it.downloader.getCurrentState() == Downloader.DownloadState.CANCELLED) {
                 toRemove << it
             } else if (it.downloader.getCurrentState() == Downloader.DownloadState.FINISHED) {
                 toRemove << it
             }
         }
         toRemove.each {
             model.downloads.remove(it)
         }
         model.clearButtonEnabled = false

    }

    private void markTrust(String tableName, TrustLevel level, def list) {
        int row = view.getSelectedTrustTablesRow(tableName)
        if (row < 0)
            return
        builder.getVariable(tableName).model.fireTableDataChanged()
        core.eventBus.publish(new TrustEvent(persona : list[row], level : level))
    }

    @ControllerAction
    void markTrusted() {
        markTrust("distrusted-table", TrustLevel.TRUSTED, model.distrusted)
        model.markTrustedButtonEnabled = false
        model.markNeutralFromDistrustedButtonEnabled = false
    }

    @ControllerAction
    void markNeutralFromDistrusted() {
        markTrust("distrusted-table", TrustLevel.NEUTRAL, model.distrusted)
        model.markTrustedButtonEnabled = false
        model.markNeutralFromDistrustedButtonEnabled = false
    }

    @ControllerAction
    void markDistrusted() {
        markTrust("trusted-table", TrustLevel.DISTRUSTED, model.trusted)
        model.subscribeButtonEnabled = false
        model.markDistrustedButtonEnabled = false
        model.markNeutralFromTrustedButtonEnabled = false
    }

    @ControllerAction
    void markNeutralFromTrusted() {
        markTrust("trusted-table", TrustLevel.NEUTRAL, model.trusted)
        model.subscribeButtonEnabled = false
        model.markDistrustedButtonEnabled = false
        model.markNeutralFromTrustedButtonEnabled = false
    }

    @ControllerAction
    void subscribe() {
        int row = view.getSelectedTrustTablesRow("trusted-table")
        if (row < 0)
            return
        Persona p = model.trusted[row]
        core.muOptions.trustSubscriptions.add(p)
        saveMuWireSettings()
        core.eventBus.publish(new TrustSubscriptionEvent(persona : p, subscribe : true))
        model.subscribeButtonEnabled = false
        model.markDistrustedButtonEnabled = false
        model.markNeutralFromTrustedButtonEnabled = false
    }

    @ControllerAction
    void review() {
        RemoteTrustList list = getSelectedTrustList()
        if (list == null)
            return
        Map<String,Object> env = new HashMap<>()
        env["trustList"] = list
        env["trustService"] = core.trustService
        env["eventBus"] = core.eventBus
        mvcGroup.createMVCGroup("trust-list", env)

    }

    @ControllerAction
    void update() {
        RemoteTrustList list = getSelectedTrustList()
        if (list == null)
            return
        core.eventBus.publish(new TrustSubscriptionEvent(persona : list.persona, subscribe : true))
    }

    @ControllerAction
    void unsubscribe() {
        RemoteTrustList list = getSelectedTrustList()
        if (list == null)
            return
        core.muOptions.trustSubscriptions.remove(list.persona)
        saveMuWireSettings()
        model.subscriptions.remove(list)
        JTable table = builder.getVariable("subscription-table")
        table.model.fireTableDataChanged()
        core.eventBus.publish(new TrustSubscriptionEvent(persona : list.persona, subscribe : false))
    }

    private RemoteTrustList getSelectedTrustList() {
        int row = view.getSelectedTrustTablesRow("subscription-table")
        if (row < 0)
            return null
        model.subscriptions[row]
    }

    void unshareSelectedFile() {
        def sf = view.selectedSharedFiles()
        if (sf == null)
            return
        sf.each {  
            core.eventBus.publish(new FileUnsharedEvent(unsharedFile : it))
        }
        core.eventBus.publish(new UIPersistFilesEvent())
    }
    
    @ControllerAction
    void addComment() {
        def selectedFiles = view.selectedSharedFiles()
        if (selectedFiles == null || selectedFiles.isEmpty())
            return
        
        Map<String, Object> params = new HashMap<>()
        params['selectedFiles'] = selectedFiles
        params['core'] = core
        mvcGroup.createMVCGroup("add-comment", "Add Comment", params)
    }
    
    @ControllerAction
    void clearUploads() {
        model.uploads.removeAll { it.finished }
    }
    
    @ControllerAction
    void restoreSession() {
        model.sessionRestored = true
        view.settings.openTabs.each { 
            performSearch(it)
        }
    }
    
    @ControllerAction
    void issueCertificate() {
        view.selectedSharedFiles().each { 
            core.eventBus.publish(new UICreateCertificateEvent(sharedFile : it))
        }
    }

    void saveMuWireSettings() {
        core.saveMuSettings()
    }

    void mvcGroupInit(Map<String, String> args) {
        application.addPropertyChangeListener("core", {e->
            core = e.getNewValue()
        })
    }
}