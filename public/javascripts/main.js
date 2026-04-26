$(document).ready(function () {
    $(".alert").delay(3000).fadeOut(500);

    $(".ajax-toggle-form").on("submit", function (event) {
        event.preventDefault();

        const form = $(this);
        const url = form.attr("action");
        const button = form.find("button");
        const listItem = form.closest("li");
        const title = listItem.find(".todo-title");

        button.prop("disabled", true);
        button.text("Updating...");

        $.ajax({
            url: url,
            type: "POST",
            data: form.serialize(),
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            },
            success: function (data) {
                if (data.success) {
                    if (data.isCompleted) {
                        title.addClass("completed");
                    } else {
                        title.removeClass("completed");
                    }
                } else {
                    alert("Todo durumu güncellenemedi.");
                }
            },
            error: function () {
                alert("Todo durumu güncellenirken hata oluştu.");
            },
            complete: function () {
                button.prop("disabled", false);
                button.text("Toggle");
            }
        });
    });

    $("#todo-title-input").on("input", function () {
        const currentLength = $(this).val().length;
        $("#todo-title-counter").text(currentLength + " / 200");
    });

    $("#todo-description-input").on("input", function () {
        const currentLength = $(this).val().length;
        $("#todo-description-counter").text(currentLength + " / 1000");
    });
});