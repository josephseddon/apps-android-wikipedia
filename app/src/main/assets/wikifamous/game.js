(function () {
  var bridge = window.WikiFamousBridge;
  var state = null;
  var elapsedMs = 0;
  var timerHandle = null;
  var roundStartTime = 0;
  var bonusWindowMs = 3000;

  function applyTheme() {
    try {
      var theme = JSON.parse(bridge.getThemeJson());
      var root = document.documentElement.style;
      Object.keys(theme).forEach(function (key) {
        root.setProperty('--color-' + key, theme[key]);
      });
    } catch (e) {
      // Fall back to the default palette baked into style.css.
    }
  }

  function winningArticle(round) {
    return round.article1.views >= round.article2.views ? round.article1 : round.article2;
  }

  function formatViews(views) {
    return views.toLocaleString();
  }

  function startTimer() {
    roundStartTime = Date.now();
    elapsedMs = 0;
    clearInterval(timerHandle);
    timerHandle = setInterval(function () {
      elapsedMs = Date.now() - roundStartTime;
      updateTimerDisplay();
    }, 100);
  }

  function updateTimerDisplay() {
    var el = document.getElementById('timer');
    if (!el) return;
    el.textContent = (elapsedMs / 1000).toFixed(1) + 's';
    el.className = 'timer' + (elapsedMs <= bonusWindowMs ? ' bonus' : '');
  }

  function selectArticle(title) {
    var round = state.rounds[state.currentRoundIndex];
    if (round.selectedTitle) return;
    clearInterval(timerHandle);
    state = JSON.parse(bridge.submitAnswer(title));
    render();
  }

  function nextRound() {
    state = JSON.parse(bridge.goToNextRound());
    startTimer();
    render();
  }

  function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text || '';
    return div.innerHTML;
  }

  function encodeTitleAttr(title) {
    return escapeHtml(title).replace(/"/g, '&quot;');
  }

  function articleCardHtml(article, round) {
    var answered = !!round.selectedTitle;
    var winner = winningArticle(round);
    var isWinner = winner.title === article.title;
    var isSelected = round.selectedTitle === article.title;
    var classes = ['article-card'];
    if (answered) {
      classes.push('disabled');
      if (isWinner) classes.push('winner');
      else if (isSelected) classes.push('selected-wrong');
    }
    var thumb = article.thumbnailUrl
      ? '<img class="article-thumb" src="' + article.thumbnailUrl + '">'
      : '<div class="article-thumb"></div>';
    var views = answered
      ? '<div class="article-views ' + (isWinner ? 'winner' : 'not-winner') + '">' +
        formatViews(article.views) + ' views in the last 30 days</div>'
      : '';
    return '<div class="' + classes.join(' ') + '" data-title="' + encodeTitleAttr(article.title) + '">' +
      thumb +
      '<div class="article-body">' +
      '<p class="article-title">' + escapeHtml(article.title) + '</p>' +
      '<p class="article-extract">' + escapeHtml(article.extract) + '</p>' +
      views +
      '</div></div>';
  }

  function render() {
    var app = document.getElementById('app');
    app.classList.remove('loading');
    if (!state || !state.rounds || !state.rounds.length) {
      app.textContent = "Unable to load today's game.";
      return;
    }
    var round = state.rounds[state.currentRoundIndex];
    var answered = !!round.selectedTitle;

    var html = '';
    html += '<div class="round-header">' +
      '<span class="round-label">Round ' + (state.currentRoundIndex + 1) + ' of ' + state.rounds.length + '</span>' +
      '<span class="score-label">Score: ' + state.score + '</span>' +
      '</div>';
    if (!answered) {
      html += '<div id="timer" class="timer bonus">0.0s</div>';
    }
    html += '<div class="question-prompt">Which article got more page views in the last 30 days?</div>';
    html += articleCardHtml(round.article1, round);
    html += articleCardHtml(round.article2, round);

    if (answered) {
      var correct = round.answeredCorrectly === true;
      html += '<div class="feedback ' + (correct ? 'correct' : 'incorrect') + '">' +
        (correct ? 'Correct!' : 'Incorrect') + '</div>';
      if (round.respondedWithinBonusWindow) {
        html += '<div class="speed-bonus">Speed bonus +5</div>';
      }
      html += '<button class="next-button" id="nextButton">Next</button>';
    }

    app.innerHTML = html;

    if (!answered) {
      Array.prototype.forEach.call(app.querySelectorAll('.article-card'), function (el) {
        el.addEventListener('click', function () {
          selectArticle(el.getAttribute('data-title'));
        });
      });
    } else {
      var nextButton = document.getElementById('nextButton');
      if (nextButton) nextButton.addEventListener('click', nextRound);
    }
  }

  window.addEventListener('DOMContentLoaded', function () {
    applyTheme();
    bonusWindowMs = bridge.getSpeedBonusWindowMs();
    state = JSON.parse(bridge.getGameStateJson());
    startTimer();
    render();
  });
})();
