/**
 * 여행 도우미 마스코트 위젯 - Live2D 캐릭터 렌더링 + 채팅 UI.
 */
(function () {
    const MODEL_URL = '/model/vivian/%E8%96%87%E8%96%87%E5%AE%89.model3.json?v=2';
    const STORAGE_KEY = 'tanoshimi_companion_chat_history';
    const MAX_STORED_TURNS = 20;

    const canvas = document.getElementById('companion-canvas');
    const toggleBtn = document.getElementById('companion-toggle');
    const panel = document.getElementById('companion-chat-panel');
    const closeBtn = document.getElementById('companion-chat-close');
    const log = document.getElementById('companion-chat-log');
    const input = document.getElementById('companion-chat-input');
    const sendBtn = document.getElementById('companion-chat-send');

    
    const showBotChatCheckbox = document.getElementById('companion-show-bot-chat');
    if (showBotChatCheckbox) {
        showBotChatCheckbox.addEventListener('change', () => {
            const logPanel = document.getElementById('companion-chat-log');
            if(logPanel) logPanel.innerHTML = '';
            renderHistory();
        });
    }

    if (!canvas) return;


    let live2dModel = null;

    const styleEl = document.createElement("style");
    styleEl.innerHTML = `
      @keyframes dangle {
          0% { transform: scale(var(--vivian-scale, 1)) rotate(-5deg) translateY(0); }
          100% { transform: scale(var(--vivian-scale, 1)) rotate(5deg) translateY(-10px); }
      }
      .dangle-animate {
          animation: dangle 0.4s infinite alternate ease-in-out;
      }
    `;
    document.head.appendChild(styleEl);

    async function initLive2D() {
        try {
            if (typeof PIXI === 'undefined' || !PIXI.live2d) {
                console.warn('[companion] Live2D 라이브러리 로드 실패');
                return;
            }

            const APP_WIDTH = 240;
            const APP_HEIGHT = 320;

            const app = new PIXI.Application({
                view: canvas, transparent: true, autoStart: true,
                width: APP_WIDTH, height: APP_HEIGHT, preserveDrawingBuffer: true,
                resolution: Math.max(window.devicePixelRatio || 1, 2) * 2.5,
                autoDensity: true,
            });
            const model = await PIXI.live2d.Live2DModel.from(MODEL_URL);
            app.stage.addChild(model);

            const baseScale = Math.min(APP_WIDTH / model.width, APP_HEIGHT / model.height);
            model.scale.set(baseScale);
            model.x = (APP_WIDTH - (model.width)) / 2;
            model.y = (APP_HEIGHT - model.height);

            function updateScale(uiScale) {
                canvas.style.setProperty('--vivian-scale', uiScale);
                const bubble = document.getElementById('companion-speech-bubble');
                if (bubble) bubble.style.setProperty('--vivian-scale', uiScale);
                canvas.style.transform = 'scale(' + uiScale + ')';
                canvas.style.transformOrigin = 'bottom right';
            }
            const scaleSlider = document.getElementById('companion-scale-slider');
            const scaleVal = document.getElementById('companion-scale-val');
            let storedScale = localStorage.getItem('companion_scale');
            if (storedScale) {
                storedScale = parseFloat(storedScale);
                if (scaleVal) scaleVal.textContent = storedScale.toFixed(1);
                updateScale(storedScale);
                if (scaleSlider) scaleSlider.value = storedScale;
            } else {
                updateScale(1.0);
            }
            if (scaleSlider) {
                scaleSlider.addEventListener('input', (e) => {
                    const val = parseFloat(e.target.value);
                    if (scaleVal) scaleVal.textContent = val.toFixed(1);
                    updateScale(val);
                    localStorage.setItem('companion_scale', val);
                });
            }

            const exprBtns = document.getElementById('companion-expr-btns');
            if (exprBtns) {
                exprBtns.addEventListener('click', (e) => {
                    if (e.target.tagName === 'BUTTON') {
                        const expr = e.target.getAttribute('data-expr');
                        if (expr) react(expr);
                    }
                });
            }
            
            const ghostBtn = document.getElementById('companion-ghost-btn');
            if (ghostBtn) ghostBtn.addEventListener('click', () => { if(window.toggleCharacterVisibility) window.toggleCharacterVisibility(); });

            live2dModel = model;
            
            live2dModel.internalModel.on('beforeModelUpdate', () => {
                if (window.currentVivianEmotion && live2dModel.internalModel.coreModel) {
                    live2dModel.internalModel.coreModel.addParameterValueById(window.currentVivianEmotion, 1.0);
                }
            });

                                    window.isDraggingVivian = false;

                        window.isDraggingVivian = false;

                        window.isDraggingVivian = false;

            function makeDraggable(elId, handleId, storageKey) {
                const el = document.getElementById(elId);
                const handle = document.getElementById(handleId);
                if (!el || !handle) return;
                let isDown = false, startX, startY, startLeft, startTop;

                // For character, we want exact pixel hover detection
                if (elId === "companion-character-wrap") {
                    handle.style.pointerEvents = 'none'; // Default to transparent
                    window.addEventListener('pointermove', (e) => {
                        if (window.isDraggingVivian) {
                            handle.style.pointerEvents = 'auto';
                            return;
                        }

                        const rect = handle.getBoundingClientRect();
                        if (e.clientX < rect.left || e.clientX > rect.right || e.clientY < rect.top || e.clientY > rect.bottom) {
                            handle.style.pointerEvents = 'none';
                            return;
                        }

                        const gl = handle.getContext('webgl2') || handle.getContext('webgl');
                        if (!gl) return;
                        
                        try {
                            const pixels = new Uint8Array(4);
                            const px = (e.clientX - rect.left) * (handle.width / rect.width);
                            const py = (e.clientY - rect.top) * (handle.height / rect.height);
                            gl.readPixels(px, handle.height - py, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
                            handle.style.pointerEvents = (pixels[3] === 0) ? 'none' : 'auto';
                        } catch(err) {}
                    }, { passive: true });
                }

                handle.addEventListener('pointerdown', (e) => {
                    if (e.target.tagName === 'BUTTON' || e.target.tagName === 'INPUT') return;
                    isDown = true;
                    if (elId === "companion-character-wrap") window.isDraggingVivian = true;
                    
                    startX = e.clientX;
                    startY = e.clientY;
                    const rect = el.getBoundingClientRect();
                    startLeft = rect.left;
                    startTop = rect.top;
                    el.style.right = 'auto';
                    el.style.bottom = 'auto';
                    el.style.left = startLeft + 'px';
                    el.style.top = startTop + 'px';
                    handle.style.cursor = 'grabbing';
                    
                    if(elId === "companion-character-wrap") {
                        handle.classList.add("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 10px 15px rgba(0,0,0,0.4))";
                        react('shy');
                    }
                });
                
                window.addEventListener('pointermove', (e) => {
                    if (!isDown) return;
                    e.preventDefault();
                    el.style.left = (startLeft + e.clientX - startX) + 'px';
                    el.style.top = (startTop + e.clientY - startY) + 'px';
                }, { passive: false });
                
                window.addEventListener('pointerup', () => {
                    if(el.id === "companion-character-wrap") {
                        handle.classList.remove("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 4px 6px rgba(0,0,0,0.2))";
                        react('normal');
                        if (isDown) window.isDraggingVivian = false;
                    }

                    if (isDown) {
                        isDown = false;
                        handle.style.cursor = 'grab';
                        try {
                            const rect = el.getBoundingClientRect();
                            const pos = { left: rect.left, top: rect.top };
                            localStorage.setItem(storageKey, JSON.stringify(pos));
                        } catch (e) {}
                    }
                });

                if (elId === "companion-character-wrap") {
                    handle.addEventListener('dblclick', (e) => {
                        react('angry');
                    });
                }
            }

            makeDraggable('companion-character-wrap', 'companion-canvas', 'companion_pos_char');
            makeDraggable('companion-chat-panel', 'companion-chat-drag-handle', 'companion_pos_chat');
            

            window.addEventListener('pointermove', (e) => {
                const rect = canvas.getBoundingClientRect();
                model.focus(e.clientX - rect.left, e.clientY - rect.top);
            });

            const chatOpenBtn = document.getElementById('companion-chat-open');
            if (chatOpenBtn) {
                chatOpenBtn.addEventListener('click', () => {
                    react('greet');
                    if(panel.style.display === 'none' || !panel.style.display) {
                        togglePanel();
                    }
                });
            }
        } catch (e) {
            console.warn('[companion] Live2D 모델 로드 실패:', e);
        }
    }

    function analyzeEmotion(text) {
        if (!text) return 'normal';
        const k = text;
        if (/(화나|짜증|흥|불쾌|경멸|싸늘|차갑|인상|노려|찌푸리|흥칫뿡|삐진|분노)/.test(k)) return 'angry';
        if (/(슬프|훌쩍|당황|당혹|놀라|어머|앗|눈물|울먹|우울|걱정|허둥지둥|으앙)/.test(k)) return 'sad';
        if (/(부끄|발그레|화끈|수줍|붉히)/.test(k)) return 'shy';
        if (/(웃|미소|행복|기쁘|즐거|신나|하하|후훗|방긋|환하|활짝)/.test(k)) return 'happy';
        return 'normal';
    }

        function react(emotion) {
        if (!live2dModel) return;
        const motionByEmotion = { greet: 'tap_body', thinking: 'flick_head', happy: 'tap_body', angry: 'tap_body', sad: 'flick_head', shy: 'tap_body', normal: 'tap_body' };
        try { live2dModel.motion(motionByEmotion[emotion] || 'tap_body'); } catch (e) { }
        try {
            if (emotion === 'sad') window.currentVivianEmotion = 'Param144';
            else if (emotion === 'shy' || emotion === 'happy') window.currentVivianEmotion = 'Param149';
            else if (emotion === 'angry') window.currentVivianEmotion = 'Param150';
            else if (emotion === 'thinking') window.currentVivianEmotion = 'Param132';
            else window.currentVivianEmotion = null;

            // 3초 뒤에 원래 표정으로 리셋
            if (window.vivianEmotionTimeout) clearTimeout(window.vivianEmotionTimeout);
            if (window.currentVivianEmotion !== null) {
                window.vivianEmotionTimeout = setTimeout(() => {
                    window.currentVivianEmotion = null;
                }, 3000);
            }
        } catch (e) { console.warn('표정 변경 실패', e); }
    }
    initLive2D();

    function loadHistory() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            return raw ? JSON.parse(raw) : [];
        } catch (e) { return []; }
    }
    function saveHistory(history) {
        const trimmed = history.slice(-MAX_STORED_TURNS);
        try { localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed)); } catch (e) { }
    }

    let history = loadHistory();

    function renderHistory() {
        log.innerHTML = '';
        if (history.length === 0) {
            appendBubble('bot', '(당신을 보며 반색하며) 오셨군요. 여행 준비는 제가 도와드릴 테니 신경 쓰지 말고 편하게 말씀하세요.');
            return;
        }
        history.forEach(turn => appendBubble(turn.role === 'user' ? 'user' : 'bot', turn.content));
    }

        function appendBubble(who, text) {
        if (who === 'bot') {
            const popup = document.getElementById('companion-speech-bubble');
            if (popup) {
                popup.innerText = text;
                popup.style.display = 'block';
                if (window.companionSpeechTimeout) clearTimeout(window.companionSpeechTimeout);
                window.companionSpeechTimeout = setTimeout(() => {
                    popup.style.display = 'none';
                }, 12000);
            }
            const showCheck = document.getElementById('companion-show-bot-chat');
            if (showCheck && !showCheck.checked) {
                return;
            }
        }

        const div = document.createElement('div');
        div.className = 'companion-msg ' + who;

        div.style.padding = '8px 12px';
        div.style.borderRadius = '12px';
        div.style.maxWidth = '80%';
        div.style.wordBreak = 'break-word';
        div.style.marginBottom = '6px';
        div.style.fontSize = '14px';

        if (who === 'user') {
            div.style.alignSelf = 'flex-end';
            div.style.background = '#4A6741';
            div.style.color = 'white';
        } else {
            div.style.alignSelf = 'flex-start';
            div.style.background = 'white';
            div.style.color = '#333';
            div.style.border = '1px solid #ddd';
        }

        div.textContent = text;
        log.appendChild(div);
        log.scrollTop = log.scrollHeight;
    }

    function togglePanel() {
        const opening = panel.style.display === 'none' || !panel.style.display;
        panel.style.display = opening ? 'flex' : 'none';
        canvas.style.transition = 'transform 0.3s ease';
        canvas.style.transformOrigin = 'bottom right';
        const sc = opening ? 0.8 : (parseFloat(localStorage.getItem('companion_scale') || 1.0));
        canvas.style.setProperty('--vivian-scale', sc);
        const bubble = document.getElementById('companion-speech-bubble');
        if (bubble) bubble.style.setProperty('--vivian-scale', sc);
        canvas.style.transform = 'scale(' + sc + ')';
        if (opening) {
            renderHistory();
            input.focus();
        }
    }
        window.toggleCharacterVisibility = function(forceHide) {
        const isCurrentlyHidden = canvas.style.opacity === '0';
        const willHide = forceHide !== undefined ? forceHide : !isCurrentlyHidden;
        
        canvas.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
        canvas.style.opacity = willHide ? '0' : '1';
        canvas.style.pointerEvents = willHide ? 'none' : 'auto';
        
        if (toggleBtn) {
            toggleBtn.textContent = willHide ? '🙈' : '👀';
            toggleBtn.title = willHide ? '캐릭터 보이기' : '캐릭터 숨기기';
        }
        const ghostBtn = document.getElementById('companion-ghost-btn');
        if (ghostBtn) {
            ghostBtn.innerHTML = willHide ? '👻 표시하기' : '👻 숨기기';
            ghostBtn.style.background = willHide ? '#a5d6a7' : '#ffeb3b';
        }
        
        localStorage.setItem('companion_hidden_state', willHide ? 'true' : 'false');
    };
    
    
    const savedHidden = localStorage.getItem('companion_hidden_state');
    if (savedHidden === 'true') {
        window.toggleCharacterVisibility(true);
    }

    toggleBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        window.toggleCharacterVisibility();
    });
    closeBtn?.addEventListener('click', () => { panel.style.display = 'none'; });

    const clearBtn = document.getElementById('companion-chat-clear');
    clearBtn?.addEventListener('click', () => {
        if (confirm('모든 대화 기록을 초기화하시겠습니까?')) {
            history = [];
            localStorage.removeItem(STORAGE_KEY);
            log.innerHTML = '';
            // 초기 인사말 다시 추가
            setTimeout(() => {
                const initMsg = document.createElement('div');
                initMsg.style.alignSelf = 'flex-start';
                initMsg.style.background = 'white';
                initMsg.style.color = 'black';
                initMsg.style.padding = '8px 12px';
                initMsg.style.borderRadius = '12px';
                initMsg.style.maxWidth = '80%';
                initMsg.style.lineHeight = '1.4';
                initMsg.style.wordBreak = 'break-word';
                initMsg.style.boxShadow = '0 1px 3px rgba(0,0,0,0.1)';
                initMsg.innerHTML = '📝 **새로운 시작이네요!** 다시 안내해 드릴까요?';
                log.appendChild(initMsg);
            }, 300);
        }
    });

    async function sendMessage() {
        const text = input.value.trim();
        if (!text) return;
        input.value = '';
        input.disabled = true;
        sendBtn.disabled = true;
        react('thinking');

        appendBubble('user', text);
        history.push({ role: 'user', content: text });

        let csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
        let csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

        try {
            const res = await fetch('/api/companion/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrfToken ? {[csrfHeader]: csrfToken} : {})
                },
                body: JSON.stringify({
                    history: history.slice(0, -1),
                    message: text
                })
            });
            const result = await res.json();

            const reply = result.success ? result.data : (result.message || '어라, 응답을 못 받았어 🥺');
            appendBubble('bot', reply);
            history.push({ role: 'assistant', content: reply });
            saveHistory(history);
            const detectedEmotion = analyzeEmotion(reply);
            react(detectedEmotion);
        } catch (e) {
            appendBubble('bot', '어라, 지금 통신이 잘 안 되네... 조금 뒤에 다시 말 걸어줄래? 📡');
        } finally {
            input.disabled = false;
            sendBtn.disabled = false;
            input.focus();
        }
    }

    sendBtn?.addEventListener('click', sendMessage);
    input?.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') sendMessage();
    });
})();
