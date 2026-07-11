package com.kamalpost.taskmanager.data

import android.content.Context
import com.kamalpost.taskmanager.data.room.RoomTaskStore

fun provideTaskStore(context: Context): TaskStore = RoomTaskStore(context)
