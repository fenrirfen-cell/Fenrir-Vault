package com.nuvio.app.assistant

import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State and actions behind the assistant panel. */
object AssistantController {
    data class Message(val fromUser: Boolean, val text: String)

    data class Countdown(
        val launch: AssistantLaunch,
        val request: AssistantStreamRequest,
        val heading: String,
        val detail: String,
        val lookingFor: String,
        val secondsLeft: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _countdown = MutableStateFlow<Countdown?>(null)
    val countdown: StateFlow<Countdown?> = _countdown.asStateFlow()
    private val _choices = MutableStateFlow<List<MetaPreview>>(emptyList())
    val choices: StateFlow<List<MetaPreview>> = _choices.asStateFlow()

    private var job: Job? = null
    private var lastCommand: AssistantCommand? = null

    val voiceAvailable: Boolean get() = AssistantPlayback.startVoice != null

    fun show() {
        AssistantSettings.ensureLoaded()
        _open.value = true
    }

    fun hide() {
        if (_countdown.value != null) {
            job?.cancel()
            _countdown.value = null
        }
        _open.value = false
    }

    fun startVoice() {
        AssistantPlayback.startVoice?.invoke { spoken -> submit(spoken) }
    }

    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        job?.cancel()
        _countdown.value = null
        _choices.value = emptyList()
        _busy.value = false
        addMessage(fromUser = true, text = text)

        val command = AssistantCommandParser.parse(text)
        if (command == null) {
            addMessage(false, "Tell me what to play, like \"next episode of The Rookie in English\" or \"play Dune in 4K\".")
            return
        }
        lastCommand = command
        _busy.value = true
        job = scope.launch {
            val result = runCatching { AssistantResolver.resolve(command) }
                .getOrElse { AssistantResolver.Result.Failed("Something went wrong: ${it.message ?: "unknown error"}") }
            _busy.value = false
            handle(command, result)
        }
    }

    fun pick(item: MetaPreview) {
        val command = lastCommand ?: return
        job?.cancel()
        _choices.value = emptyList()
        addMessage(fromUser = true, text = item.name)
        _busy.value = true
        job = scope.launch {
            val result = runCatching {
                AssistantResolver.resolveMeta(command, item.type, item.id, item.name, item.poster, item.banner, item.logo)
            }.getOrElse { AssistantResolver.Result.Failed("Something went wrong: ${it.message ?: "unknown error"}") }
            _busy.value = false
            handle(command, result)
        }
    }

    fun cancelCountdown() {
        if (_countdown.value == null) return
        job?.cancel()
        _countdown.value = null
        addMessage(false, "Cancelled.")
    }

    fun playNow() {
        val current = _countdown.value ?: return
        job?.cancel()
        play(current.launch, current.request)
    }

    private suspend fun handle(command: AssistantCommand, result: AssistantResolver.Result) {
        when (result) {
            is AssistantResolver.Result.Failed -> addMessage(false, result.message)
            is AssistantResolver.Result.Choices -> {
                addMessage(false, result.message)
                _choices.value = result.options
            }
            is AssistantResolver.Result.Ready -> {
                val request = AssistantStreamRequest(
                    prefs = command.effectivePreferences(AssistantSettings.prefs.value),
                    relaxLanguage = command.relaxLanguage,
                    season = result.launch.season,
                    episode = result.launch.episode,
                )
                addMessage(false, "Playing ${result.heading}.")
                var left = request.prefs.countdownSeconds.coerceIn(0, 10)
                while (left > 0) {
                    _countdown.value = Countdown(
                        launch = result.launch,
                        request = request,
                        heading = result.heading,
                        detail = result.detail,
                        lookingFor = AssistantStreamRanker.describe(request),
                        secondsLeft = left,
                    )
                    delay(1_000L)
                    left--
                }
                play(result.launch, request)
            }
        }
    }

    private fun play(launch: AssistantLaunch, request: AssistantStreamRequest) {
        _countdown.value = null
        val launcher = AssistantPlayback.launcher
        if (launcher == null) {
            addMessage(false, "I couldn't start playback from here.")
            return
        }
        AssistantPlayback.arm(launch, request)
        _open.value = false
        launcher(launch)
    }

    private fun addMessage(fromUser: Boolean, text: String) {
        _messages.value = (_messages.value + Message(fromUser, text)).takeLast(30)
    }
}
