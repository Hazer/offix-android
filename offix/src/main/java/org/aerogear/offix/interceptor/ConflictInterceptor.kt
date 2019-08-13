package org.aerogear.offix.interceptor

import android.util.Log
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import org.aerogear.offix.ConflictResolutionHandler
import org.aerogear.offix.conflictedMutationClass
import org.aerogear.offix.interfaces.ConfliceResolutionInterface
import java.util.concurrent.Executor

class ConflictInterceptor(private val conflictResolutionImpl: ConfliceResolutionInterface) : ApolloInterceptor {

    private val TAG = javaClass.simpleName
    private lateinit var userCallback: ApolloInterceptor.CallBack

    override fun interceptAsync(
        request: ApolloInterceptor.InterceptorRequest,
        chain: ApolloInterceptorChain,
        dispatcher: Executor,
        callBack: ApolloInterceptor.CallBack
    ) {
        userCallback = callBack
        //Check if this is a mutation request.
        if (request.operation !is Mutation) {
            //Not a mutation. Nothing to do here - move on to the next link in the chain.
            chain.proceedAsync(request, dispatcher, callBack)
            return
        }
        Log.d("$TAG 1", request.requestHeaders.headers().toString())
        chain.proceedAsync(request, dispatcher, OffixConflictCallback(conflictResolutionImpl))
    }

    override fun dispose() {
        Log.v(TAG, "Dispose called")
    }

    inner class OffixConflictCallback(val conflictResolutionImpl: ConfliceResolutionInterface) :
        ApolloInterceptor.CallBack {
        private val TAG = javaClass.simpleName

        override fun onResponse(response: ApolloInterceptor.InterceptorResponse) {

            /* Check if the conflict is present in the response of not using the ConflictResolutionHandler class.
             */
            if (ConflictResolutionHandler().conflictPresent(response.parsedResponse)) {

                /* Parse the response from the server into a Map object and extract the serverState and clientState.
                   Make an object of ServerClientData and add to the list.
                */
                val conflictInfo =
                    (((response.parsedResponse.get().errors()[0] as Error).customAttributes()["extensions"] as Map<*, *>)["exception"] as Map<*, *>)["conflictInfo"] as Map<*, *>

                val serverStateMap = conflictInfo["serverState"] as Map<String, Any>
                val clientStateMap = conflictInfo["clientState"] as Map<String, Any>

                conflictResolutionImpl.resolveConflict(serverStateMap, clientStateMap, conflictedMutationClass)
            } else {
                userCallback.onResponse(response)
            }
        }

        override fun onFetch(sourceType: ApolloInterceptor.FetchSourceType?) {
            Log.d(TAG, "onFetch()")
        }

        override fun onCompleted() {
            Log.d(TAG, "onCompleted()")
        }

        override fun onFailure(e: ApolloException) {
            userCallback.onFailure(e)
            Log.d(TAG, "onFailure()")
        }
    }
}