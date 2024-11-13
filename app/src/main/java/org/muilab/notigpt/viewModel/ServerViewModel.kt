package org.muilab.notigpt.viewModel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak")
    private val context = getApplication<Application>().applicationContext

    private val _response = MutableLiveData<String>()
    val response: LiveData<String> = _response

    fun sortNotis() {
//        viewModelScope.launch {
//            try {
//                val responseStr = geminiService.makeRequest("sort")
//                Log.d("Gemini", responseStr)
//                _response.postValue(responseStr)
//
//                val listType = object : TypeToken<List<SortOutcome>>() {}.type
//                val outcomeList: List<SortOutcome> = Gson().fromJson(responseStr, listType)
//
//                CoroutineScope(Dispatchers.IO).launch {
//                    val drawerDatabase = DrawerDatabase.getInstance(context)
//                    val drawerDao = drawerDatabase.drawerDao()
//                    val updateNotis = mutableListOf<NotiUnit>()
//
//                    outcomeList.forEach {
//                        val hashKey = it.id
//                        val score = (it.timeSensitiveness + it.senderAttractiveness + it.contentAttractiveness)
//                            .toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
//                        val existingNoti = drawerDao.getByHashKey(hashKey)
//                        if (existingNoti.isNotEmpty())
//                            updateNotis.add(
//                                existingNoti[0].copy(
//                                    outcome = existingNoti[0].outcome.copy(score = score)
//                                )
//                            )
//                    }
//                    drawerDao.updateList(updateNotis)
//                }
//                Toast.makeText(context, "Sort Complete", Toast.LENGTH_SHORT).show()
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
    }
}