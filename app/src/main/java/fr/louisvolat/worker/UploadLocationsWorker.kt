package fr.louisvolat.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class UploadLocationsWorker(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams) {


    override fun doWork(): Result {
        TODO("Not yet implemented")
    }

}