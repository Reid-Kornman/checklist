package com.example.checklist

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ManageRoutinesActivity : AppCompatActivity() {

    private lateinit var taskManager: TaskManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RoutineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_routines)

        taskManager = TaskManager(this)

        recyclerView = findViewById(R.id.routinesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = RoutineAdapter(taskManager,
            onView = { routineId ->
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("routine_id", routineId)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            },
            onEdit = { routineId ->
                val intent = Intent(this, EditRoutineActivity::class.java)
                intent.putExtra("routine_id", routineId)
                startActivity(intent)
            },
            onDelete = { routineId ->
                taskManager.deleteRoutine(routineId)
                refreshList()
            }
        )
        recyclerView.adapter = adapter

        val addRoutineButton = findViewById<Button>(R.id.addRoutineButton)
        addRoutineButton.setOnClickListener {
            val intent = Intent(this, EditRoutineActivity::class.java)
            startActivity(intent)
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        adapter.updateRoutines(taskManager.getRoutines())
    }
}
