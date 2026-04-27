$(document).ready(function () {

    $(".alert").delay(3000).fadeOut(500);

    const csrfToken = $('meta[name="csrf-token"]').attr("content");

    $('.ajax-toggle-form').on('submit', function (e) {
        e.preventDefault();

        const form = $(this);
        const todoId = form.data('todo-id');

        const button = form.find('.toggle-btn');
        const todoItem = $('.todo-item[data-todo-id="' + todoId + '"]');

        const title = todoItem.find('.todo-title');
        const badge = todoItem.find('.todo-status-badge');

        button.prop('disabled', true);
        button.text('Updating...');

        $.ajax({
            url: form.attr('action'),
            type: 'POST',
            data: form.serialize(),
            headers: {
                "X-CSRF-Token": csrfToken,
                "X-Requested-With": "XMLHttpRequest"
            },
            success: function (data) {

                if (data.success) {

                    if (data.isCompleted) {
                        // TITLE
                        title.addClass('text-decoration-line-through text-muted');

                        // BADGE
                        badge
                            .removeClass('text-bg-warning')
                            .addClass('text-bg-success')
                            .text('Completed');

                        // BUTTON
                        button
                            .removeClass('btn-outline-success')
                            .addClass('btn-outline-warning')
                            .text('Undo');

                    } else {
                        // TITLE
                        title.removeClass('text-decoration-line-through text-muted');

                        // BADGE
                        badge
                            .removeClass('text-bg-success')
                            .addClass('text-bg-warning')
                            .text('Active');

                        // BUTTON
                        button
                            .removeClass('btn-outline-warning')
                            .addClass('btn-outline-success')
                            .text('Complete');
                    }

                } else {
                    alert(data.message || "Todo güncellenemedi.");
                }
            },
            error: function () {
                alert("Toggle sırasında hata oluştu.");
            },
            complete: function () {
                button.prop('disabled', false);
            }
        });
    });

    $('.delete-form').on('submit', function (e) {
        if (!confirm('Bu todo silinsin mi?')) {
            e.preventDefault();
        }
    });

    $('.admin-status-form').on('submit', function (e) {
        const message = $(this).data('message') || 'Bu işlem yapılsın mı?';

        if (!confirm(message)) {
            e.preventDefault();
        }
    });

    $("#todo-title-input").on("input", function () {
        $("#todo-title-counter").text($(this).val().length + " / 200");
    });

    $("#todo-description-input").on("input", function () {
        $("#todo-description-counter").text($(this).val().length + " / 1000");
    });

});