
setInterval(function(){
	jQuery.get('c:/tmp/web-display.log', function(data) {
	//process text file line by line
	//$('#logContent').html(data.replace('n',''));
	$('#logContent').val(data);
	})
}, 1000);
