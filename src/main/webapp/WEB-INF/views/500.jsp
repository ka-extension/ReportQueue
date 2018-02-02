<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
	<head>
		<title>Internal server error</title>
		<meta charset="utf-8"  />
		<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/styles/main.css"  />
		<link href="https://fonts.googleapis.com/css?family=Roboto:300,400" rel="stylesheet"  />
		<link rel="icon" href="<%= request.getContextPath() %>/favicon.ico" type="image/x-icon"/>
	</head>
	<body>
		<div id="wrapper">
			<div id="container">
				<h1>500</h1>
				<p>Internal server error</p>
			</div>
		</div>
	</body>
</html>