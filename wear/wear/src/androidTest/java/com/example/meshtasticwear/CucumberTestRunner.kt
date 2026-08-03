package com.example.meshtasticwear

import android.os.Bundle
import io.cucumber.android.runner.CucumberAndroidJUnitRunner

class CucumberTestRunner : CucumberAndroidJUnitRunner() {
    
    override fun onCreate(bundle: Bundle) {
        // Points to the assets/features folder containing the .feature files
        bundle.putString("features", "features")
        
        // Package where the Step Definitions are located
        bundle.putString("glue", "com.example.meshtasticwear")
        
        super.onCreate(bundle)
    }
}
