$(document).ready(function () {
    let emailCheckTimer = null;

    $("#email").on("input", function () {
        const email = $(this).val().trim();
        const message = $("#email-check-message");

        clearTimeout(emailCheckTimer);

        if (message.length === 0) {
            return;
        }

        if (email.length < 5 || !email.includes("@")) {
            message.text("");
            message.removeClass("field-success field-error");
            return;
        }

        message.text("Email kontrol ediliyor...");
        message.removeClass("field-success field-error");

        emailCheckTimer = setTimeout(function () {
            $.ajax({
                url: "/check-email",
                type: "GET",
                data: {
                    email: email
                },
                success: function (data) {
                    message.text(data.message);

                    if (data.available) {
                        message.removeClass("field-error").addClass("field-success");
                    } else {
                        message.removeClass("field-success").addClass("field-error");
                    }
                },
                error: function () {
                    message.text("Email kontrol edilirken hata oluştu.");
                    message.removeClass("field-success").addClass("field-error");
                }
            });
        }, 500);
    });

    $("#password, #confirmPassword").on("input", function () {
        const password = $("#password").val();
        const confirmPassword = $("#confirmPassword").val();
        const message = $("#password-match-message");

        if (message.length === 0) {
            return;
        }

        if (confirmPassword.length === 0) {
            message.text("");
            message.removeClass("field-success field-error");
            return;
        }

        if (password === confirmPassword) {
            message.text("Şifreler eşleşiyor.");
            message.removeClass("field-error").addClass("field-success");
        } else {
            message.text("Şifreler eşleşmiyor.");
            message.removeClass("field-success").addClass("field-error");
        }
    });
});