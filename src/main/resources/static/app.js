(function () {
  "use strict";

  async function callApi(url, options) {
    const response = await fetch(url, options);
    let data = null;
    try {
      data = await response.json();
    } catch (err) {
      data = null;
    }
    return { ok: response.ok, status: response.status, data: data };
  }

  function show(element) {
    element.hidden = false;
  }

  function hide(element) {
    element.hidden = true;
  }

  // Every reason a request can fail already comes back from the API as a machine-readable
  // `error` code (see docs/api.yaml) — this table only translates each one into a plain
  // sentence, it never re-detects failures the server has already classified.
  var ERROR_MESSAGES = {
    ALIAS_TAKEN: "That alias is already taken. Try a different one.",
    URL_ALREADY_SHORTENED: "This URL already has a short link under a different code.",
    RATE_LIMITED: "You're going a bit fast — please try again shortly."
  };

  function describeError(status, data) {
    if (data && data.error === "VALIDATION_ERROR" && data.message) {
      // The validator's own message already names the specific rule that failed.
      return data.message;
    }
    if (data && ERROR_MESSAGES[data.error]) {
      return ERROR_MESSAGES[data.error];
    }
    return "Something went wrong. Please try again.";
  }

  function initShortenForm() {
    var form = document.getElementById("shorten-form");
    var errorBox = document.getElementById("shorten-error");
    var resultBox = document.getElementById("shorten-result");
    var urlInput = document.getElementById("url-input");
    var aliasInput = document.getElementById("alias-input");
    var expirationSelect = document.getElementById("expiration-select");
    var shortUrlOutput = document.getElementById("short-url-output");
    var copyButton = document.getElementById("copy-button");
    var copyConfirmation = document.getElementById("copy-confirmation");

    form.addEventListener("submit", function (event) {
      event.preventDefault();
      hide(errorBox);
      hide(resultBox);
      hide(copyConfirmation);

      var payload = { url: urlInput.value.trim() };
      if (aliasInput.value.trim() !== "") {
        payload.alias = aliasInput.value.trim();
      }
      if (expirationSelect.value !== "") {
        payload.expiresInSeconds = parseInt(expirationSelect.value, 10);
      }

      callApi("/api/links", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      }).then(function (result) {
        if (result.ok) {
          shortUrlOutput.value = result.data.shortUrl;
          show(resultBox);
        } else {
          errorBox.textContent = describeError(result.status, result.data);
          show(errorBox);
        }
      }).catch(function () {
        errorBox.textContent = "Couldn't reach the server. Please try again.";
        show(errorBox);
      });
    });

    copyButton.addEventListener("click", function () {
      var value = shortUrlOutput.value;
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(value).then(function () {
          copyConfirmation.textContent = "Copied!";
          show(copyConfirmation);
        }).catch(function () {
          fallbackCopy();
        });
      } else {
        fallbackCopy();
      }
    });

    function fallbackCopy() {
      shortUrlOutput.select();
      copyConfirmation.textContent = "Selected — press Ctrl+C (or Cmd+C) to copy.";
      show(copyConfirmation);
    }
  }

  function formatTimestamp(value) {
    if (!value) {
      return "Never";
    }
    return new Date(value).toLocaleString();
  }

  function initStatsForm() {
    var form = document.getElementById("stats-form");
    var codeInput = document.getElementById("code-input");
    var resultBox = document.getElementById("stats-result");
    var notFoundBox = document.getElementById("stats-not-found");

    form.addEventListener("submit", function (event) {
      event.preventDefault();
      hide(resultBox);
      hide(notFoundBox);

      var code = codeInput.value.trim();
      callApi("/api/links/" + encodeURIComponent(code) + "/stats", { method: "GET" })
        .then(function (result) {
          if (result.ok) {
            document.getElementById("stats-clicks").textContent = result.data.clickCount;
            document.getElementById("stats-created").textContent = formatTimestamp(result.data.createdAt);
            document.getElementById("stats-last-used").textContent = formatTimestamp(result.data.lastAccessedAt);
            document.getElementById("stats-expires").textContent = formatTimestamp(result.data.expiresAt);
            show(resultBox);
          } else {
            show(notFoundBox);
          }
        }).catch(function () {
          notFoundBox.textContent = "Couldn't reach the server. Please try again.";
          show(notFoundBox);
        });
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    initShortenForm();
    initStatsForm();
  });
})();
