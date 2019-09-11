/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.parser

import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.webkit.MimeTypeMap
import org.readium.r2.shared.Link
import org.readium.r2.shared.Publication
import org.readium.r2.streamer.container.ContainerCbz
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.experimental.and


/**
 *      CbzParser : Handle any CBZ file. Opening, listing files
 *                  get name of the resource, creating the Publication
 *                  for rendering
 */

class CbzParser : PublicationParser {

    companion object {
        // Some constants useful to parse an Cbz document
        const val mimetypeCBZ = "application/vnd.comicbook+zip"
        const val mimetypeCBR = "application/x-cbr"

        const val mimetypeJPEG = "image/jpeg"
        const val mimetypePNG = "image/png"

//        Remember .zip files are .cbz; .rar files are .cbr; and .tar files are .cbt.
//        http://fileformats.archiveteam.org/wiki/Comic_Book_Archive

//        The format is fairly simple. First you take the scanned images of each page of the comic
//        (usually in PNG or JPEG, but TIFF, GIF, and BMP have been used)
//        and give them filenames that sort in order of the page number (e.g., 0001.png, 0002.png, etc.).
//        Then compress them into an archive using ZIP, RAR, TAR, ACE, or 7z.
//        Finally, change the file extension to signify a comic book archive:


// Extensions
//        .cbz for ZIP format
//        .cbr for RAR format
//        .cbt for TAR format
//        .cba for ACE archive
//        .cb7 for 7z archive

// Mimetypes
//        application/vnd.comicbook+zip,
//        application/vnd.comicbook-rar,
//        application/x-cbr
    }

    /**
     * Check if path exist, generate a container for CBZ file
     *                   then check if creation was a success
     */
    private fun generateContainerFrom(path: String): ContainerCbz {
        val container: ContainerCbz?

        if (!File(path).exists())
            throw Exception("Missing File")
        container = ContainerCbz(path)
        if (!container.successCreated)
            throw Exception("Missing File")
        return container
    }

    //TODO Comment that code
    /**
     *
     */
    override fun parse(fileAtPath: String, title: String): PubBox? {
        val container = try {
            generateContainerFrom(fileAtPath)
        } catch (e: Exception) {
            Timber.e(e, "Could not generate container")
            return null
        }
        val listFiles = try {
            container.getFilesList()
        } catch (e: Exception) {
            Timber.e(e, "Missing File : META-INF/container.xml")
            return null
        }

        val publication = Publication()

        listFiles.forEach {
            val link = Link()

            link.typeLink = getMimeType(it)
            link.href = it

            if (getMimeType(it) == mimetypeJPEG || getMimeType(it) == mimetypePNG) {
                publication.readingOrder.add(link)
            } else {
                publication.resources.add(link)
            }
        }
        val hash = fileToMD5(fileAtPath)
        publication.readingOrder.first().rel.add("cover")

        // Add href as title if title is missing (this is used to display the TOC)
        for ((index, link) in publication.readingOrder.withIndex()) {
            if (link.title == null || link.title!!.isEmpty()) {
                link.title = link.href
            }
        }

        publication.readingOrder = publication.readingOrder.sortedWith(compareBy {it.href}).toMutableList()

        publication.metadata.identifier = hash!!
        publication.type = Publication.TYPE.CBZ
        return PubBox(publication, container)
    }

    private fun getMimeType(file: String): String? {
        return try {
            val lastSegment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val path = Paths.get(file)
                path.fileName.toString()
            } else {
                val uri = Uri.parse(file);
                uri.lastPathSegment
            }
            var type: String? = null
            val name = lastSegment?.replace(" ", "")?.replace("'", "")?.replace(",", "")
            val extension = MimeTypeMap.getFileExtensionFromUrl(name)
            if (!TextUtils.isEmpty(extension)) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)!!
            } else {
                val reCheckExtension = MimeTypeMap.getFileExtensionFromUrl(name?.replace("\\s+", ""))
                if (!TextUtils.isEmpty(reCheckExtension)) {
                    type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(reCheckExtension)!!
                }
            }
            type
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    private fun fileToMD5(filePath: String): String? {
        var inputStream: InputStream? = null
        try {
            inputStream = FileInputStream(filePath)
            val buffer = ByteArray(1024)
            val digest = MessageDigest.getInstance("MD5")
            var numRead = 0
            while (numRead != -1) {
                numRead = inputStream.read(buffer)
                if (numRead > 0)
                    digest.update(buffer, 0, numRead)
            }
            val md5Bytes = digest.digest()
            return convertHashToString(md5Bytes)
        } catch (e: Exception) {
            return null
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: Exception) {
                }

            }
        }
    }

    private fun convertHashToString(md5Bytes: ByteArray): String {
        var returnVal = ""
        for (i in md5Bytes.indices) {
            returnVal += Integer.toString((md5Bytes[i] and 0xff.toByte()) + 0x100, 16).substring(1)
        }
        return returnVal
    }
}