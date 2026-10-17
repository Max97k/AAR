package com.aar.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestVerificationTest {

    private fun loadManifestXml(): org.w3c.dom.Document {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        val manifestFile = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find AndroidManifest.xml in candidates: $candidates")

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(manifestFile)
    }

    private fun loadServiceConfigXml(): org.w3c.dom.Document {
        val candidates = listOf(
            File("src/main/res/xml/accessibility_service_config.xml"),
            File("app/src/main/res/xml/accessibility_service_config.xml"),
            File("../app/src/main/res/xml/accessibility_service_config.xml")
        )
        val configFile = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find accessibility_service_config.xml in candidates: $candidates")

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(configFile)
    }

    @Test
    fun `manifest must NEVER declare INTERNET permission`() {
        val doc = loadManifestXml()
        val permissionNodes = doc.getElementsByTagName("uses-permission")
        for (i in 0 until permissionNodes.length) {
            val node = permissionNodes.item(i)
            val name = node.attributes.getNamedItem("android:name")?.nodeValue ?: ""
            assertFalse(
                "VIOLATION: Zero-network policy violated! Manifest declares: $name",
                name.equals("android.permission.INTERNET", ignoreCase = true)
            )
            assertFalse(
                "VIOLATION: Manifest must not declare ACCESS_NETWORK_STATE",
                name.equals("android.permission.ACCESS_NETWORK_STATE", ignoreCase = true)
            )
        }
    }

    @Test
    fun `manifest must declare AutoReaderAccessibilityService with BIND_ACCESSIBILITY_SERVICE`() {
        val doc = loadManifestXml()
        val serviceNodes = doc.getElementsByTagName("service")
        var foundAccessibilityService = false

        for (i in 0 until serviceNodes.length) {
            val node = serviceNodes.item(i)
            val name = node.attributes.getNamedItem("android:name")?.nodeValue ?: ""
            if (name.contains("AutoReaderAccessibilityService")) {
                foundAccessibilityService = true
                val permission = node.attributes.getNamedItem("android:permission")?.nodeValue
                val exported = node.attributes.getNamedItem("android:exported")?.nodeValue
                assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", permission)
                assertEquals("true", exported)

                // Check meta-data for config resource
                var foundMetaData = false
                val children = node.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeName == "meta-data") {
                        val metaName = child.attributes.getNamedItem("android:name")?.nodeValue
                        val metaResource = child.attributes.getNamedItem("android:resource")?.nodeValue
                        if (metaName == "android.accessibilityservice") {
                            foundMetaData = true
                            assertEquals("@xml/accessibility_service_config", metaResource)
                        }
                    }
                }
                assertTrue("AutoReaderAccessibilityService missing meta-data android.accessibilityservice", foundMetaData)
            }
        }
        assertTrue("AutoReaderAccessibilityService was not declared in AndroidManifest.xml", foundAccessibilityService)
    }

    @Test
    fun `manifest must declare AutoReaderTileService with BIND_QUICK_SETTINGS_TILE`() {
        val doc = loadManifestXml()
        val serviceNodes = doc.getElementsByTagName("service")
        var foundTileService = false

        for (i in 0 until serviceNodes.length) {
            val node = serviceNodes.item(i)
            val name = node.attributes.getNamedItem("android:name")?.nodeValue ?: ""
            if (name.contains("AutoReaderTileService")) {
                foundTileService = true
                val permission = node.attributes.getNamedItem("android:permission")?.nodeValue
                val exported = node.attributes.getNamedItem("android:exported")?.nodeValue
                assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", permission)
                assertEquals("true", exported)

                // Check intent-filter
                var foundAction = false
                val children = node.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeName == "intent-filter") {
                        val actions = child.childNodes
                        for (k in 0 until actions.length) {
                            val actionNode = actions.item(k)
                            if (actionNode.nodeName == "action") {
                                val actionName = actionNode.attributes.getNamedItem("android:name")?.nodeValue
                                if (actionName == "android.service.quicksettings.action.QS_TILE") {
                                    foundAction = true
                                }
                            }
                        }
                    }
                }
                assertTrue("AutoReaderTileService missing action android.service.quicksettings.action.QS_TILE", foundAction)
            }
        }
        assertTrue("AutoReaderTileService was not declared in AndroidManifest.xml", foundTileService)
    }

    @Test
    fun `accessibility_service_config must have valid flags and gesture support`() {
        val doc = loadServiceConfigXml()
        val root = doc.documentElement
        assertEquals("accessibility-service", root.nodeName)

        val canRetrieveWindowContent = root.getAttribute("android:canRetrieveWindowContent")
        val canPerformGestures = root.getAttribute("android:canPerformGestures")
        val notificationTimeout = root.getAttribute("android:notificationTimeout")
        val flags = root.getAttribute("android:accessibilityFlags")

        assertEquals("true", canRetrieveWindowContent)
        assertEquals("true", canPerformGestures)
        assertEquals("150", notificationTimeout)
        assertTrue("Flags must include flagRetrieveInteractiveWindows", flags.contains("flagRetrieveInteractiveWindows"))
        assertTrue("Flags must include flagReportViewIds", flags.contains("flagReportViewIds"))
    }
}
