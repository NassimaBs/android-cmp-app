package com.sourcepointmeta.test_project

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sourcepointmeta.cmplibrary.ConsentLib
import com.sourcepointmeta.cmplibrary.ConsentLibBuilder

class MainActivity : AppCompatActivity() {

    var consentLibBuilder:ConsentLibBuilder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        consentLibBuilder = ConsentLib.newBuilder(22,"mobile.demo",2372,"5c0e81b7d74b3c30c6852301",this)
        consentLibBuilder!!.setStage(true)
        consentLibBuilder!!.setMessageTimeOut(15000)
        consentLibBuilder!!.setViewGroup(findViewById(android.R.id.content))
        consentLibBuilder!!.setShowPM(false)
        consentLibBuilder!!.setOnMessageReady(object :ConsentLib.Callback{
            override fun run(c: ConsentLib) {         }
        } )
        consentLibBuilder!!.setOnConsentReady(object : ConsentLib.Callback {
            override fun run(c: ConsentLib) {          }

        })
        consentLibBuilder!!.setOnErrorOccurred(object : ConsentLib.Callback{
            override fun run(c: ConsentLib) {           }
        })
        consentLibBuilder!!.build().run()
    }


}


