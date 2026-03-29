package com.example.checklist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RoutineAdapter(
    private val taskManager: TaskManager,
    private val onView: (routineId: String) -> Unit,
    private val onEdit: (routineId: String) -> Unit,
    private val onDelete: (routineId: String) -> Unit
) : RecyclerView.Adapter<RoutineAdapter.RoutineViewHolder>() {

    private val routines = mutableListOf<Routine>()

    fun updateRoutines(newRoutines: List<Routine>) {
        routines.clear()
        routines.addAll(newRoutines)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoutineViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_routine, parent, false)
        return RoutineViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoutineViewHolder, position: Int) {
        holder.bind(routines[position])
    }

    override fun getItemCount() = routines.size

    inner class RoutineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val routineName: TextView = itemView.findViewById(R.id.routineName)
        private val routineTime: TextView = itemView.findViewById(R.id.routineTime)
        private val viewButton: Button = itemView.findViewById(R.id.viewRoutineButton)
        private val editButton: Button = itemView.findViewById(R.id.editRoutineButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteRoutineButton)

        fun bind(routine: Routine) {
            routineName.text = routine.name
            routineTime.text = "${toAmPm(routine.startTime)} - ${toAmPm(routine.endTime)}"
            viewButton.setOnClickListener { onView(routine.id) }
            editButton.setOnClickListener { onEdit(routine.id) }
            deleteButton.setOnClickListener { onDelete(routine.id) }
        }
    }
}
