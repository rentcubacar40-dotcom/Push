package com.example.data.model

import com.google.gson.annotations.SerializedName

data class MoodleTokenResponse(
    @SerializedName("token") val token: String? = null,
    @SerializedName("privatetoken") val privatetoken: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("errorcode") val errorcode: String? = null,
    @SerializedName("message") val message: String? = null
)

data class SiteInfoResponse(
    @SerializedName("userid") val userid: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("fullname") val fullname: String? = null,
    @SerializedName("firstname") val firstname: String? = null,
    @SerializedName("lastname") val lastname: String? = null,
    @SerializedName("usercontextid") val usercontextid: Long? = null,
    @SerializedName("userprivateaccesskey") val userprivateaccesskey: String? = null,
    @SerializedName("siteurl") val siteurl: String? = null,
    @SerializedName("sitename") val sitename: String? = null
)

data class UploadResponseItem(
    @SerializedName("component") val component: String? = null,
    @SerializedName("contextid") val contextid: Long? = null,
    @SerializedName("userId") val userId: Long? = null,
    @SerializedName("filearea") val filearea: String? = null,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("filepath") val filepath: String? = null,
    @SerializedName("itemid") val itemid: Long? = null,
    @SerializedName("license") val license: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("timecreated") val timecreated: Long? = null,
    @SerializedName("timemodified") val timemodified: Long? = null,
    @SerializedName("mimetype") val mimetype: String? = null,
    @SerializedName("url") val url: String? = null
)

data class FileListResponse(
    @SerializedName("parents") val parents: List<Any>? = null,
    @SerializedName("files") val files: List<RemoteFileItem>? = null,
    @SerializedName("exception") val exception: String? = null,
    @SerializedName("errorcode") val errorcode: String? = null,
    @SerializedName("message") val message: String? = null
)

data class RemoteFileItem(
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("filepath") val filepath: String? = null,
    @SerializedName("filesize") val filesize: Long? = null,
    @SerializedName("fileurl") val fileurl: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("contextid") val contextid: Long? = null,
    @SerializedName("itemid") val itemid: Long? = null,
    @SerializedName("timemodified") val timemodified: Long? = null,
    @SerializedName("timecreated") val timecreated: Long? = null,
    @SerializedName("mimetype") val mimetype: String? = null,
    @SerializedName("author") val author: String? = null
) {
    val effectiveUrl: String?
        get() = if (!url.isNullOrEmpty()) url else fileurl
}

data class UserEvidenceListPageResponse(
    @SerializedName("evidence") val evidence: List<UserEvidenceItem>? = null,
    @SerializedName("canmanage") val canmanage: Boolean? = null
)

data class UserEvidenceItem(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("files") val files: List<RemoteFileItem>? = null
)

data class EvidenceUploadResult(
    val evidenceId: Long,
    val filename: String,
    val directUrl: String
)
