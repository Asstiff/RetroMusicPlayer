/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package code.name.monkey.retromusic.service.notification

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import code.name.monkey.appthemehelper.util.ColorUtil
import code.name.monkey.appthemehelper.util.MaterialValueHelper
import code.name.monkey.retromusic.Constants.ACTION_QUIT
import code.name.monkey.retromusic.Constants.ACTION_REWIND
import code.name.monkey.retromusic.Constants.ACTION_SKIP
import code.name.monkey.retromusic.Constants.ACTION_TOGGLE_PAUSE
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.activities.MainActivity
import code.name.monkey.retromusic.glide.GlideApp
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroSimpleTarget
import code.name.monkey.retromusic.glide.palette.BitmapPaletteWrapper
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.service.MusicService
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.RetroUtil
import code.name.monkey.retromusic.util.RetroUtil.createBitmap
import code.name.monkey.retromusic.util.color.MediaNotificationProcessor
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition

/**
 * @author Hemanth S (h4h13).
 */
class PlayingNotificationOreo : PlayingNotification() {

    private var target: Target<BitmapPaletteWrapper>? = null

    private fun getCombinedRemoteViews(collapsed: Boolean, song: Song): RemoteViews {
        val remoteViews = RemoteViews(service.packageName,
                if (collapsed) R.layout.layout_notification_collapsed else R.layout.layout_notification_expanded)

        remoteViews.setTextViewText(R.id.appName, service.getString(R.string.app_name) + " • " + song.albumName)
        remoteViews.setTextViewText(R.id.title, song.title)
        remoteViews.setTextViewText(R.id.subtitle, song.artistName)

        val typedArray = service.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val selectableItemBackground = typedArray.getResourceId(0, 0)
        typedArray.recycle()

        remoteViews.setInt(R.id.content, "setBackgroundResource", selectableItemBackground)

        linkButtons(remoteViews)

        //setNotificationContent(remoteViews, ColorUtil.isColorLight(backgroundColor));
        return remoteViews
    }

    override fun update() {
        stopped = false
        val song = service.currentSong
        val isPlaying = service.isPlaying

        val notificationLayout = getCombinedRemoteViews(true, song)
        val notificationLayoutBig = getCombinedRemoteViews(false, song)

        val action = Intent(service, MainActivity::class.java)
        action.putExtra("expand", true)
        action.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val clickIntent = PendingIntent
                .getActivity(service, 0, action, PendingIntent.FLAG_UPDATE_CURRENT)
        val deleteIntent = buildPendingIntent(service, ACTION_QUIT, null)

        val builder = NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(clickIntent)
                .setDeleteIntent(deleteIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCustomContentView(notificationLayout)
                .setCustomBigContentView(notificationLayoutBig)
                .setOngoing(isPlaying)

        val bigNotificationImageSize = service.resources
                .getDimensionPixelSize(R.dimen.notification_big_image_size)
        service.runOnUiThread {
            if (target != null) {
                GlideApp.with(service).clear(target);
            }
            target = GlideApp.with(service)
                    .asBitmapPalette()
                    .load(RetroGlideExtension.getSongModel(song))
                    .transition(RetroGlideExtension.getDefaultTransition())
                    .songOptions(song)
                    .into(object : RetroSimpleTarget<BitmapPaletteWrapper>(bigNotificationImageSize, bigNotificationImageSize) {
                        override fun onResourceReady(resource: BitmapPaletteWrapper, transition: Transition<in BitmapPaletteWrapper>?) {
                            val mediaNotificationProcessor = MediaNotificationProcessor(service, service) { i, _ -> update(resource.bitmap, i) }
                            mediaNotificationProcessor.processNotification(resource.bitmap)
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            super.onLoadFailed(errorDrawable)
                            update(null, Color.WHITE)
                        }

                        private fun update(bitmap: Bitmap?, bgColor: Int) {
                            var bgColorFinal = bgColor
                            if (bitmap != null) {
                                notificationLayout.setImageViewBitmap(R.id.largeIcon, bitmap)
                                notificationLayoutBig.setImageViewBitmap(R.id.largeIcon, bitmap)
                            } else {
                                notificationLayout.setImageViewResource(R.id.largeIcon, R.drawable.default_album_art)
                                notificationLayoutBig.setImageViewResource(R.id.largeIcon, R.drawable.default_album_art)
                            }

                            if (!PreferenceUtil.getInstance().coloredNotification()) {
                                bgColorFinal = Color.WHITE
                            }
                            setBackgroundColor(bgColorFinal)
                            setNotificationContent(ColorUtil.isColorLight(bgColorFinal))

                            if (stopped) {
                                return  // notification has been stopped before loading was finished
                            }
                            updateNotifyModeAndPostNotification(builder.build())
                        }

                        private fun setBackgroundColor(color: Int) {

                            notificationLayout.setInt(R.id.image, "setBackgroundColor", color)
                            notificationLayoutBig.setInt(R.id.image, "setBackgroundColor", color)

                            notificationLayout.setInt(R.id.foregroundImage, "setColorFilter", color)
                            notificationLayoutBig.setInt(R.id.foregroundImage, "setColorFilter", color)
                        }

                        private fun setNotificationContent(dark: Boolean) {
                            val primary = MaterialValueHelper.getPrimaryTextColor(service, dark)
                            val secondary = MaterialValueHelper.getSecondaryTextColor(service, dark)

                            val close = createBitmap(RetroUtil.getTintedVectorDrawable(service, R.drawable.ic_close_white_24dp, primary)!!, PlayingNotification.NOTIFICATION_CONTROLS_SIZE_MULTIPLIER)
                            val prev = createBitmap(RetroUtil.getTintedVectorDrawable(service, R.drawable.ic_skip_previous_white_24dp, primary)!!, PlayingNotification.NOTIFICATION_CONTROLS_SIZE_MULTIPLIER)
                            val next = createBitmap(RetroUtil.getTintedVectorDrawable(service, R.drawable.ic_skip_next_white_24dp, primary)!!, PlayingNotification.NOTIFICATION_CONTROLS_SIZE_MULTIPLIER)
                            val playPause = createBitmap(RetroUtil.getTintedVectorDrawable(service,
                                    if (isPlaying)
                                        R.drawable.ic_pause_white_24dp
                                    else
                                        R.drawable.ic_play_arrow_white_32dp, primary)!!, PlayingNotification.NOTIFICATION_CONTROLS_SIZE_MULTIPLIER)

                            notificationLayout.setTextColor(R.id.title, primary)
                            notificationLayout.setTextColor(R.id.subtitle, secondary)
                            notificationLayout.setTextColor(R.id.appName, secondary)

                            notificationLayout.setImageViewBitmap(R.id.action_prev, prev)
                            notificationLayout.setImageViewBitmap(R.id.action_next, next)
                            notificationLayout.setImageViewBitmap(R.id.action_play_pause, playPause)

                            notificationLayoutBig.setTextColor(R.id.title, primary)
                            notificationLayoutBig.setTextColor(R.id.subtitle, secondary)
                            notificationLayoutBig.setTextColor(R.id.appName, secondary)

                            notificationLayoutBig.setImageViewBitmap(R.id.action_quit, close)
                            notificationLayoutBig.setImageViewBitmap(R.id.action_prev, prev)
                            notificationLayoutBig.setImageViewBitmap(R.id.action_next, next)
                            notificationLayoutBig.setImageViewBitmap(R.id.action_play_pause, playPause)

                            notificationLayout.setImageViewBitmap(R.id.smallIcon, createBitmap(RetroUtil.getTintedVectorDrawable(service, R.drawable.ic_notification, secondary)!!, 0.6f))
                            notificationLayoutBig.setImageViewBitmap(R.id.smallIcon, createBitmap(RetroUtil.getTintedVectorDrawable(service, R.drawable.ic_notification, secondary)!!, 0.6f))

                            notificationLayout.setInt(R.id.arrow, "setColorFilter", secondary)
                            notificationLayoutBig.setInt(R.id.arrow, "setColorFilter", secondary)

                        }
                    })
        }

        if (stopped) {
            return  // notification has been stopped before loading was finished
        }
        updateNotifyModeAndPostNotification(builder.build())
    }


    private fun buildPendingIntent(context: Context, action: String,
                                   serviceName: ComponentName?): PendingIntent {
        val intent = Intent(action)
        intent.component = serviceName
        return PendingIntent.getService(context, 0, intent, 0)
    }


    private fun linkButtons(notificationLayout: RemoteViews) {
        var pendingIntent: PendingIntent

        val serviceName = ComponentName(service, MusicService::class.java)

        // Previous track
        pendingIntent = buildPendingIntent(service, ACTION_REWIND, serviceName)
        notificationLayout.setOnClickPendingIntent(R.id.action_prev, pendingIntent)

        // Play and pause
        pendingIntent = buildPendingIntent(service, ACTION_TOGGLE_PAUSE, serviceName)
        notificationLayout.setOnClickPendingIntent(R.id.action_play_pause, pendingIntent)

        // Next track
        pendingIntent = buildPendingIntent(service, ACTION_SKIP, serviceName)
        notificationLayout.setOnClickPendingIntent(R.id.action_next, pendingIntent)

        // Close
        pendingIntent = buildPendingIntent(service, ACTION_QUIT, serviceName)
        notificationLayout.setOnClickPendingIntent(R.id.action_quit, pendingIntent)
    }

}
