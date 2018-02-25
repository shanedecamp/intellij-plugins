package org.stepik.api.client.serialization

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import org.stepik.api.client.Loggable
import org.stepik.api.objects.attempts.Dataset
import org.stepik.api.objects.attempts.DatasetDeserializer
import org.stepik.api.objects.steps.BlockView
import org.stepik.api.objects.steps.BlockViewDeserializer
import org.stepik.api.objects.submissions.Reply
import org.stepik.api.objects.submissions.ReplyDeserializer


object DefaultJsonConverter : JsonConverter, Loggable {
    private val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Dataset::class.java, DatasetDeserializer())
            .registerTypeAdapter(Reply::class.java, ReplyDeserializer())
            .registerTypeAdapter(BlockView::class.java, BlockViewDeserializer())
            .create()

    private val pretty_gson: Gson = GsonBuilder()
            .registerTypeAdapter(Dataset::class.java, DatasetDeserializer())
            .registerTypeAdapter(Reply::class.java, ReplyDeserializer())
            .registerTypeAdapter(BlockView::class.java, BlockViewDeserializer())
            .setPrettyPrinting()
            .serializeNulls()
            .create()


    override fun <T> fromJson(json: String?, clazz: Class<T>): T? {
        if (json == null) {
            return null
        }

        try {
            return gson.fromJson(json, clazz)
        } catch (e: JsonSyntaxException) {
            logger.warn("Failed ${clazz.simpleName} fromJson $json ", e)
            return null
        }
    }

    override fun toJson(any: Any?, pretty: Boolean): String {
        return (if (pretty) pretty_gson else gson).toJson(any)
    }

}
