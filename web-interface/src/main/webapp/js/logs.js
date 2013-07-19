$(document).ready(function() {
	function logLevelToString(logLevel) {
		if (logLevel == 1) return "TRACE";
        if (logLevel == 2) return "DEBUG";
        if (logLevel == 3) return "INFO";
        if (logLevel == 4) return "WARN";
        if (logLevel == 5) return "ERROR";

        return "" + logLevel;
    }

	$("table.logs tr.data").click(function(e) {
		if(e.target.nodeName == 'A') return; //skip this handler, don't return false

        var log = JSON.parse($(this).attr("data-json"));

		var content = $("<div>");
		var d = Date.create(log.time).format("{year}-{MM}-{dd} {H}:{mm}:{ss}.{fff}");
		content.append($("<p>").append($("<b>Timestamp:</b><br>")).append($("<span>").text(d)));
		content.append($("<p>").append($("<b>Task ID:</b><br>")).append($("<span>").text(log.taskId)));
		content.append($("<p>").append($("<b>Level:</b><br>")).append($("<span>").text(logLevelToString(log.level))));
		content.append($("<p>").append($("<b>Class:</b><br>")).append($("<span>").text(log.name)));
		content.append($("<p>").append($("<b>Thread name:</b><br>")).append($("<span>").text(log.threadName)));
		content.append($("<p>").append($("<b>Message:</b><br>")).append($("<span>").text(log.message)));
		var e = $("<pre>").text(log.errorTrace);
		if (! log.errorTrace) e = "–";
		content.append($("<p>").append($("<b>Error trace:</b><br>")).append(e));

		$.fancybox({
            'content': content.html()
        });
        return false;
	});
});