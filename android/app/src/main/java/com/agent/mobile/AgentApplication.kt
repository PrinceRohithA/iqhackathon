package com.agent.mobile

import android.app.Application
import com.agent.mobile.core.AppContainer

class AgentApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        container.start()
    }

    companion object {
        lateinit var instance: AgentApplication
            private set
    }
}
