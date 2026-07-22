package com.pararam2006.cmv.ui.selectApps

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectAppsScreenViewModel(
    private val context: Application,
    private val repository: AppsInfoRepository,
) : ViewModel() {
    private val pm = context.packageManager
    private val _uiState = MutableStateFlow(SelectAppsScreenUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.Default) {
                // 1. Ищем плееры (ваш исходный код)
                val mediaIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                val mediaApps = pm.queryBroadcastReceivers(mediaIntent, 0).map { it.activityInfo.packageName }

                // 2. Ищем проводники через интент выбора любых файлов (Google Files обязан его обрабатывать)
                val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                val getContentApps = pm.queryIntentActivities(getContentIntent, 0).map { it.activityInfo.packageName }

                // 3. Ищем проводники, которые умеют открывать папки в системе
                val openDocTreeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                val docTreeApps = pm.queryIntentActivities(openDocTreeIntent, 0).map { it.activityInfo.packageName }
                val knownFileManagers = listOf(
                    "com.google.android.apps.nbu.files",      // Files by Google
                    "com.google.android.documentsui",        // Системные Файлы (AOSP)
                    "com.android.providers.downloads",       // Системные Загрузки
                    "com.mi.android.globalFileexplorer",     // Проводник Xiaomi (если актуально)
                    "com.sec.android.app.myfiles"            // Мои файлы Samsung (если актуально)
                )

                // Объединяем все пакеты в один список и убираем дубликаты
                val allPackages = (mediaApps + getContentApps + docTreeApps + knownFileManagers)
                    .distinct()
                    .filter { pkgName ->
                        if (pkgName == context.packageName) return@filter false

                        try {
                            val launchIntent = pm.getLaunchIntentForPackage(pkgName)
                            val isSystemUiDocs = pkgName == "com.google.android.documentsui" || pkgName == "com.android.providers.downloads"

                            // Пропускаем только приложения с иконкой в меню или важные системные аппы
                            launchIntent != null || isSystemUiDocs
                        } catch (e: Exception) {
                            false
                        }
                    }

                // Маппим в объекты AppInfo
                allPackages.map { pkgName ->
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    val isSelected = isAppSelected(pkgName)

                    val appDetails = AppInfo(
                        label = pm.getApplicationLabel(appInfo).toString(),
                        iconUri = "android.resource://$pkgName/${appInfo.icon}".toUri(),
                        packageName = pkgName,
                        name = appInfo.name ?: "",
                        selected = isSelected,
                    )

                    if (repository.getAppInfo(pkgName) == null) {
                        repository.addAppInfo(appDetails)
                    }
                    appDetails
                }.sortedBy { it.label }
            }

            _uiState.update {
                it.copy(
                    apps = apps
                )
            }
        }
    }

    fun toogleApp(packageName: String, newState: Boolean) {
        //Оптимистичное обновление
        _uiState.update { currentState ->
            val updatedApps = currentState.apps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(selected = newState)
                } else {
                    app
                }
            }
            currentState.copy(apps = updatedApps)
        }

        viewModelScope.launch {
            if (newState) {
                repository.selectApp(packageName)
            } else {
                repository.unselectApp(packageName)
            }
        }
    }

    private suspend fun isAppSelected(packageName: String): Boolean {
        return repository.getAppInfo(packageName)?.selected ?: false
    }
}