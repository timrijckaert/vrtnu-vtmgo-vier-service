package be.tapped.vrtnu.content

import arrow.core.Either
import arrow.core.computations.either
import be.tapped.vrtnu.content.ApiResponse.Failure.JsonParsingException
import be.tapped.vrtnu.content.ElasticSearchUrlBuilder.applySearchQuery
import be.tapped.vtmgo.common.executeAsync
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal class JsonProgramParser {
    suspend fun parse(json: String): Either<ApiResponse.Failure, List<Program>> =
        Either.catch { Json.decodeFromString<List<Program>>(json) }.mapLeft(::JsonParsingException)
}

interface ProgramRepo {

    suspend fun fetchAZPrograms(): Either<ApiResponse.Failure, ApiResponse.Success.Programs>

    suspend fun fetchProgramByName(programName: String): Either<ApiResponse.Failure, ApiResponse.Success.SingleProgram>

}

internal class HttpProgramRepo(
    private val client: OkHttpClient,
    private val jsonProgramParser: JsonProgramParser,
) : ProgramRepo {

    override suspend fun fetchAZPrograms(): Either<ApiResponse.Failure, ApiResponse.Success.Programs> {
        val programsAZSorted = client.executeAsync(
            Request.Builder()
                .get()
                .url(constructUrl(SearchQuery(transcodingStatus = "AVAILABLE")))
                .build()
        )

        return either {
            val rawAZJson = !Either.fromNullable(programsAZSorted.body).mapLeft { ApiResponse.Failure.EmptyJson }
            ApiResponse.Success.Programs(!jsonProgramParser.parse(rawAZJson.string()))
        }
    }

    override suspend fun fetchProgramByName(programName: String): Either<ApiResponse.Failure, ApiResponse.Success.SingleProgram> {
        val fetchSingleProgram = client.executeAsync(
            Request.Builder()
                .get()
                .url(
                    constructUrl(
                        SearchQuery(
                            transcodingStatus = "AVAILABLE",
                            programName = programName,
                            size = 1
                        )
                    )
                )
                .build()
        )

        return either {
            val singleProgramJson = !Either.fromNullable(fetchSingleProgram.body).mapLeft { ApiResponse.Failure.EmptyJson }
            ApiResponse.Success.SingleProgram((!jsonProgramParser.parse(singleProgramJson.string())).first())
        }
    }

    private fun constructUrl(searchQuery: SearchQuery): HttpUrl =
        HttpUrl.Builder()
            .scheme("https")
            .host("vrtnu-api.vrt.be")
            .addPathSegment("suggest")
            .applySearchQuery(searchQuery)
            .build()
}
