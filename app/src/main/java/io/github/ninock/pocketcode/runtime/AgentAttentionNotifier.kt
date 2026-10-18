package io.github.ninock.pocketcode.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import io.github.ninock.pocketcode.MainActivity
import io.github.ninock.pocketcode.R
import io.github.ninock.pocketcode.model.AgentQuestion
import io.github.ninock.pocketcode.model.RiskLevel
import io.github.ninock.pocketcode.model.ToolRequest

/**
 * "Agent needs you" notifications. While the app is in the background, a pending approval or
 * question is posted with quick actions so it can be answered from the notification shade.
 * The in-app card stays the source of truth: the notification is withdrawn as soon as the
 * request is resolved or the app comes back to the foreground.
 */
internal object AgentAttentionNotifier {
    /** Approves or denies the approval with this id, if it is still the pending one. */
    @Volatile var approvalHandler: ((approvalId: String, approved: Boolean) -> Unit)? = null

    /** Answers (or skips, with null) the question with this request id, if it is still pending. */
    @Volatile var answerHandler: ((requestId: String, answers: Map<String, String>?) -> Unit)? = null

    private sealed interface Attention {
        val key: String
        data class Approval(val projectName: String, val request: ToolRequest) : Attention {
            override val key get() = "approval:${request.approvalId}"
        }
        data class Question(val projectName: String, val question: AgentQuestion) : Attention {
            override val key get() = "question:${question.requestId}"
        }
    }

    private var appVisible = false
    private var pending: Attention? = null
    private var postedKey: String? = null
    /** Requests answered from the notification; kept so a sync racing the answer cannot repost them. */
    private val handledKeys = ArrayDeque<String>()

    @Synchronized
    fun onAppVisibilityChanged(context: Context, visible: Boolean) {
        appVisible = visible
        render(context.applicationContext)
    }

    /** Mirrors the current pending approval/question; call after every state change that may touch them. */
    @Synchronized
    fun sync(context: Context, projectName: String, approval: ToolRequest?, question: AgentQuestion?) {
        pending = when {
            approval != null -> Attention.Approval(projectName, approval)
            question != null -> Attention.Question(projectName, question)
            else -> null
        }
        render(context.applicationContext)
    }

    @Synchronized
    private fun render(context: Context) {
        val attention = pending?.takeIf { !appVisible && it.key !in handledKeys }
        if (attention == null) {
            if (postedKey != null) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            postedKey = null
            return
        }
        if (attention.key == postedKey) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val notification = when (attention) {
            is Attention.Approval -> approvalNotification(context, attention)
            is Attention.Question -> questionNotification(context, attention)
        }
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onSuccess { postedKey = attention.key }
    }

    private fun approvalNotification(context: Context, attention: Attention.Approval): android.app.Notification {
        val request = attention.request
        val body = buildString {
            append(request.explanation)
            request.commandPreview?.takeIf(String::isNotBlank)?.let { append("\n\n").append(it.take(400)) }
            if (request.commandPreview.isNullOrBlank() && request.affectedPaths.isNotEmpty()) {
                append("\n\n").append(request.affectedPaths.take(5).joinToString("\n"))
            }
        }
        val title = when (request.risk) {
            RiskLevel.HIGH -> "Risky action needs approval · ${attention.projectName}"
            else -> "Approval needed · ${attention.projectName}"
        }
        return baseBuilder(context, title, request.explanation)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .addAction(0, "Deny", actionIntent(context, 10, ACTION_DENY) { putExtra(EXTRA_ID, request.approvalId) })
            .addAction(0, "Approve", actionIntent(context, 11, ACTION_APPROVE) { putExtra(EXTRA_ID, request.approvalId) })
            .build()
    }

    private fun questionNotification(context: Context, attention: Attention.Question): android.app.Notification {
        val question = attention.question
        val first = question.questions.first()
        val body = buildString {
            append(first.question)
            first.options.forEach { option ->
                append("\n• ").append(option.label)
                if (option.description.isNotBlank()) append(" — ").append(option.description)
            }
            if (question.questions.size > 1) append("\n\n+${question.questions.size - 1} more — open PocketCode to answer")
        }
        val builder = baseBuilder(context, "Question · ${attention.projectName}", first.question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        // One single-choice question can be answered right here; anything bigger opens the app.
        if (question.questions.size == 1 && !first.multiSelect) {
            first.options.take(2).forEachIndexed { index, option ->
                builder.addAction(
                    0,
                    option.label,
                    actionIntent(context, 20 + index, ACTION_ANSWER) {
                        putExtra(EXTRA_ID, question.requestId)
                        putExtra(EXTRA_QUESTION, first.question)
                        putExtra(EXTRA_ANSWER, option.label)
                    },
                )
            }
            val remoteInput = RemoteInput.Builder(KEY_REPLY)
                .setLabel("Your answer")
                .setChoices(first.options.map { it.label }.toTypedArray())
                .build()
            val replyIntent = actionIntent(context, 30, ACTION_ANSWER, mutable = true) {
                putExtra(EXTRA_ID, question.requestId)
                putExtra(EXTRA_QUESTION, first.question)
            }
            builder.addAction(
                NotificationCompat.Action.Builder(0, "Reply", replyIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(false)
                    .build(),
            )
        }
        return builder.build()
    }

    private fun baseBuilder(context: Context, title: String, text: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    1,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)

    private fun actionIntent(
        context: Context,
        requestCode: Int,
        action: String,
        mutable: Boolean = false,
        extras: Intent.() -> Unit,
    ): PendingIntent {
        val intent = Intent(context, AgentAttentionReceiver::class.java).setAction(action).apply(extras)
        // RemoteInput fills the reply in, so that one intent has to stay mutable.
        val mutability = if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, mutability or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Agent needs you", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Approvals and questions from a coding agent while PocketCode is in the background"
            },
        )
    }

    @Synchronized
    internal fun handle(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val key = when (intent.action) {
            ACTION_APPROVE, ACTION_DENY -> {
                val handler = approvalHandler ?: return
                handler(id, intent.action == ACTION_APPROVE)
                "approval:$id"
            }
            ACTION_ANSWER -> {
                val question = intent.getStringExtra(EXTRA_QUESTION) ?: return
                val answer = intent.getStringExtra(EXTRA_ANSWER)
                    ?: RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
                if (answer.isNullOrEmpty()) return
                val handler = answerHandler ?: return
                handler(id, mapOf(question to answer))
                "question:$id"
            }
            else -> return
        }
        handledKeys.addLast(key)
        while (handledKeys.size > 16) handledKeys.removeFirst()
        // Withdraw right away; the state sync that follows the answer confirms it.
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        postedKey = null
    }

    private const val CHANNEL_ID = "agent-attention"
    private const val NOTIFICATION_ID = 43
    private const val ACTION_APPROVE = "io.github.ninock.pocketcode.ATTENTION_APPROVE"
    private const val ACTION_DENY = "io.github.ninock.pocketcode.ATTENTION_DENY"
    private const val ACTION_ANSWER = "io.github.ninock.pocketcode.ATTENTION_ANSWER"
    private const val EXTRA_ID = "id"
    private const val EXTRA_QUESTION = "question"
    private const val EXTRA_ANSWER = "answer"
    private const val KEY_REPLY = "reply"
}

class AgentAttentionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AgentAttentionNotifier.handle(context, intent)
    }
}
