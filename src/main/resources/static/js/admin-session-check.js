(function () {
    var checkIntervalMs = 5000;
    var isChecking = false;

    function checkSession() {
        if (isChecking) {
            return;
        }
        isChecking = true;

        fetch("/check-session", { credentials: "same-origin" })
            .then(function (res) {
                if (!res.ok) {
                    throw new Error("request failed");
                }
                return res.json();
            })
            .then(function (data) {
                if (!data.valid) {
                    alert("権限が変更されたため、再ログインしてください。");
                    window.location.href = "/login";
                }
            })
            .catch(function () {
            })
            .finally(function () {
                isChecking = false;
            });
    }

    setInterval(checkSession, checkIntervalMs);
})();
