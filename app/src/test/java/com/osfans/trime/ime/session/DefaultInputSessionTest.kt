/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.session

import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.CommitProto
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.core.StatusProto
import com.osfans.trime.daemon.RimeSession
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy

/**
 * Messages are fed the way `InputView.handleRimeMessage` does, in the order
 * `Rime.emitResponse` sends them.
 */
class DefaultInputSessionTest :
    StringSpec({

        fun composition(text: String) = CompositionProto(text.length, text.length, text.length, text.length, text)

        fun candidates(vararg texts: String) = Candidates.Bulk(texts.size, 0, texts.map { CandidateProto(it, "", "") }.toTypedArray())

        fun composing(schema: String = "luna_pinyin") = StatusProto(schemaId = schema, schemaName = "朙月拼音", isDisabled = false, isComposing = true, isAsciiMode = false, isAsciiPunct = false)

        val idle = StatusProto(schemaId = "luna_pinyin", schemaName = "朙月拼音", isDisabled = false, isAsciiMode = false, isAsciiPunct = false)

        fun DefaultInputSession.feed(message: RimeMessage<*>) {
            onRimeMessage(message)
            if (message is RimeMessage.CompositionMessage) onComposition(message.data)
        }

        fun DefaultInputSession.response(
            composition: CompositionProto,
            candidates: Candidates,
            status: StatusProto,
            beforeStatus: DefaultInputSession.() -> Unit = {},
        ) {
            feed(RimeMessage.CommitTextMessage(CommitProto(null)))
            feed(RimeMessage.InlinePreeditMessage(""))
            feed(RimeMessage.CompositionMessage(composition))
            when (candidates) {
                is Candidates.Bulk -> feed(RimeMessage.BulkCandidatesMessage(candidates))
                is Candidates.Paged -> feed(RimeMessage.PagedCandidatesMessage(candidates))
            }
            beforeStatus()
            feed(RimeMessage.StatusMessage(status))
        }

        fun session(api: FakeRimeApi = FakeRimeApi()) = DefaultInputSession(FakeRimeSession(api), isMainThread = { true })

        "starts empty" {
            val state = session().state.value
            state shouldBe InputState()
            state.isComposing.shouldBeFalse()
        }

        "a response is published as a whole at its status message" {
            val s = session()
            val before = s.state.value
            s.response(composition("ni"), candidates("你", "尼"), composing()) {
                // everything but the status has arrived: the UI must still see the old state
                s.state.value shouldBeSameInstanceAs before
            }
            val after = s.state.value
            after.composition shouldBe composition("ni")
            after.candidates shouldBe candidates("你", "尼")
            after.status shouldBe composing()
            after.hasMenu.shouldBeTrue()
            after.isComposing.shouldBeTrue()
        }

        "a status message without an open response applies immediately" {
            val s = session()
            s.feed(RimeMessage.StatusMessage(composing()))
            s.state.value.status shouldBe composing()
        }

        "composition outside a response applies immediately" {
            // the ascii mode tip, and its removal a second later
            val s = session()
            s.feed(RimeMessage.CompositionMessage(composition("En")))
            s.state.value.composition shouldBe composition("En")
            s.state.value.isComposing.shouldBeFalse()
        }

        "a commit message while a response is open publishes the open one" {
            val s = session()
            s.feed(RimeMessage.CommitTextMessage(CommitProto(null)))
            s.feed(RimeMessage.CompositionMessage(composition("n")))
            // closing status dropped by the flow, next response starts
            s.feed(RimeMessage.CommitTextMessage(CommitProto(null)))
            s.state.value.composition shouldBe composition("n")
            s.feed(RimeMessage.CompositionMessage(composition("ni")))
            s.feed(RimeMessage.StatusMessage(composing()))
            s.state.value.composition shouldBe composition("ni")
        }

        "editor updates during an open response are neither delayed nor lost" {
            val s = session()
            s.response(composition("ni"), candidates("你"), composing()) {
                s.onSelection(3, 3)
                s.onEnterKey("发送", isPrimaryAction = true)
                s.state.value.editor.selectionStart shouldBe 3
            }
            val state = s.state.value
            state.editor.selectionStart shouldBe 3
            state.editor.selectionEnd shouldBe 3
            state.enterKey shouldBe EnterKeyState("发送", true)
            state.composition shouldBe composition("ni")
        }

        "the commit of a composition clears it together with the candidates" {
            val s = session()
            s.response(composition("ni"), candidates("你"), composing())
            s.response(CompositionProto(), Candidates.Bulk(0, 0, arrayOf()), idle)
            val state = s.state.value
            state.composition shouldBe CompositionProto()
            state.candidates.candidates.size shouldBe 0
            state.hasMenu.shouldBeFalse()
            state.isComposing.shouldBeFalse()
        }

        "an option change patches the status flag it mirrors" {
            val s = session()
            s.response(CompositionProto(), Candidates.Bulk(0, 0, arrayOf()), idle)
            s.feed(RimeMessage.OptionMessage(RimeMessage.OptionMessage.Data("ascii_mode", true)))
            s.state.value.status.isAsciiMode.shouldBeTrue()
            s.state.value.options["ascii_mode"] shouldBe true
            s.feed(RimeMessage.OptionMessage(RimeMessage.OptionMessage.Data("_liquid_keyboard", true)))
            s.state.value.options["_liquid_keyboard"] shouldBe true
            s.state.value.status shouldBe idle.copy(isAsciiMode = true)
        }

        "schema messages update the schema" {
            val s = session()
            s.feed(RimeMessage.SchemaMessage(SchemaItem("double_pinyin_flypy", "小鹤双拼")))
            s.state.value.schema shouldBe SchemaItem("double_pinyin_flypy", "小鹤双拼")
        }

        "paging mode leaves the keyboard's list empty but reports the menu" {
            val s = session()
            s.response(composition("ni"), candidates("你"), composing())
            val paged =
                Candidates.Paged(
                    hasPrevPage = true,
                    hasNextPage = true,
                    candidates = arrayOf(CandidateProto("尼", "", "1")),
                )
            s.response(CompositionProto(), paged, composing())
            val state = s.state.value
            state.candidates shouldBe Candidates.Bulk()
            state.hasMenu.shouldBeTrue()
            state.paging.shouldBeTrue()
            // back to bulk mode: no pages there
            s.response(composition("ni"), candidates("你"), composing())
            s.state.value.paging.shouldBeFalse()
        }

        "restores status, composition and menu from the engine caches" {
            val api =
                FakeRimeApi().apply {
                    statusCached = composing()
                    compositionCached = composition("ni")
                    hasMenu = true
                    candidates = arrayOf(CandidateProto("你", "", ""), CandidateProto("尼", "", ""))
                }
            val s = session(api)
            s.restoreFromEngine(CoroutineScope(Dispatchers.Unconfined), pagedMode = false)
            val state = s.state.value
            state.composition shouldBe composition("ni")
            state.status shouldBe composing()
            state.schema shouldBe SchemaItem("luna_pinyin", "朙月拼音")
            state.options["ascii_mode"] shouldBe false
            state.hasMenu.shouldBeTrue()
            state.candidates shouldBe candidates("你", "尼")
        }

        "restoring in paged mode keeps composition and list blank and fetches nothing" {
            val api =
                FakeRimeApi().apply {
                    statusCached = composing()
                    compositionCached = composition("ni")
                    hasMenu = true
                    paging = true
                    failOnFetch = true
                }
            val s = session(api)
            s.restoreFromEngine(CoroutineScope(Dispatchers.Unconfined), pagedMode = true)
            val state = s.state.value
            state.composition shouldBe CompositionProto()
            state.candidates shouldBe Candidates.Bulk()
            state.hasMenu.shouldBeTrue()
            state.paging.shouldBeTrue()
        }

        "restoring before librime answered keeps schema and options unknown" {
            val s = session()
            s.restoreFromEngine(CoroutineScope(Dispatchers.Unconfined), pagedMode = false)
            s.state.value.schema shouldBe null
            s.state.value.options shouldBe emptyMap()
        }

        "a restored candidate list older than the next response is dropped" {
            val gate = CompletableDeferred<Unit>()
            val api =
                FakeRimeApi().apply {
                    statusCached = composing()
                    compositionCached = composition("n")
                    hasMenu = true
                    candidates = arrayOf(CandidateProto("你", "", ""))
                    fetchGate = gate
                }
            val s = session(api)
            s.restoreFromEngine(CoroutineScope(Dispatchers.Unconfined), pagedMode = false)
            s.response(composition("ni"), candidates("你", "尼"), composing())
            gate.complete(Unit)
            s.state.value.candidates shouldBe candidates("你", "尼")
        }

        "updates off the main thread are rejected" {
            val s = DefaultInputSession(FakeRimeSession(FakeRimeApi()), isMainThread = { false })
            shouldThrow<IllegalStateException> { s.onSelection(1, 1) }
            shouldThrow<IllegalStateException> { s.feed(RimeMessage.StatusMessage(idle)) }
        }
    })

/** Only what the session reads; everything else throws. */
private class FakeRimeApi : RimeApi by unsupported() {
    override var statusCached = StatusProto()
    override var compositionCached = CompositionProto()
    override var hasMenu = false
    override var paging = false
    var candidates: Array<CandidateProto> = arrayOf()
    var fetchGate: CompletableDeferred<Unit>? = null
    var failOnFetch = false

    override suspend fun getCandidates(
        startIndex: Int,
        limit: Int,
    ): Array<CandidateProto> {
        check(!failOnFetch) { "unexpected candidate fetch" }
        fetchGate?.await()
        return candidates.drop(startIndex).take(limit).toTypedArray()
    }
}

private fun unsupported(): RimeApi = Proxy.newProxyInstance(RimeApi::class.java.classLoader, arrayOf(RimeApi::class.java)) { _, method, _ ->
    throw UnsupportedOperationException(method.name)
} as RimeApi

private class FakeRimeSession(
    private val api: RimeApi,
) : RimeSession {
    override fun <T> run(block: suspend RimeApi.() -> T): T = runBlocking { api.block() }

    override suspend fun <T> runOnReady(block: suspend RimeApi.() -> T): T = api.block()

    override fun runIfReady(block: suspend RimeApi.() -> Unit) = runBlocking { api.block() }

    override val lifecycleScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
}
