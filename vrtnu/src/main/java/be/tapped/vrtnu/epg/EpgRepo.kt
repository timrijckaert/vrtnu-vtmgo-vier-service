package be.tapped.vrtnu.epg

import arrow.core.Either
import arrow.core.computations.either
import be.tapped.common.executeAsync
import be.tapped.vrtnu.common.defaultOkHttpClient
import be.tapped.vrtnu.epg.ApiResponse.Failure.JsonParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*

class JsonEpgParser {
    suspend fun parse(json: String): Either<ApiResponse.Failure, Epg> =
        Either.catch { Json.decodeFromString<Epg>(json) }.mapLeft(::JsonParsingException)
}

sealed class ApiResponse {
    data class Success(val epg: Epg) : ApiResponse()
    sealed class Failure : ApiResponse() {
        data class JsonParsingException(val throwable: Throwable) : Failure()
        object EmptyJson : Failure()
    }
}

interface EpgRepo {
    suspend fun epg(calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Brussels"))): Either<ApiResponse.Failure, ApiResponse.Success>
}

class HttpEpgRepo(
    private val client: OkHttpClient = defaultOkHttpClient,
    private val jsonEpgParser: JsonEpgParser = JsonEpgParser(),
) : EpgRepo {

    // curl 'https://www.vrt.be/bin/epg/schedule.2020-11-21.json'
    private fun constructUrl(dayOfTheMonth: Int, month: Int, year: Int): HttpUrl {
        return HttpUrl.Builder()
            .scheme("https")
            .host("vrt.be")
            .addPathSegments("bin/epg")
            .addPathSegment("schedule.$year-$month-$dayOfTheMonth.json")
            .build()
    }

    override suspend fun epg(calendar: Calendar): Either<ApiResponse.Failure, ApiResponse.Success> {
        val year: Int = calendar.get(Calendar.YEAR)
        // Note that months are 0 based. 0 -> january, 11 -> december
        val month: Int = calendar.get(Calendar.MONTH) + 1
        val dayOfTheMonth: Int = calendar.get(Calendar.DAY_OF_MONTH)

        return withContext(Dispatchers.IO) {
            val epgResponse = client.executeAsync(
                Request.Builder()
                    .get()
                    .url(constructUrl(dayOfTheMonth, month, year))
                    .build()
            ).body

            either {
                val epgJson = !Either.fromNullable(epgResponse?.string()).mapLeft { ApiResponse.Failure.EmptyJson }
                ApiResponse.Success(!jsonEpgParser.parse(epgJson))
            }
        }
    }
}