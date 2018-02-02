<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="com.ethanmcdonough.ReportQueue.models.CSRF" %>
<!DOCTYPE html>
<html>
	<head>
		<title>Report</title>
		<meta charset="utf-8"  />
		<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/styles/main.css"  />
		<link href="https://fonts.googleapis.com/css?family=Roboto:300,400" rel="stylesheet"  />
		<link rel="icon" href="<%= request.getContextPath() %>/favicon.ico" type="image/x-icon"/>
	</head>
	<body>
		<div id="wrapper">
			<div id="container">
				<span id="context-path"><%= StringEscapeUtils.escapeXml11(request.getContextPath()) %></span>
				<span id="ftok"><%= StringEscapeUtils.escapeXml11(session.getAttribute(CSRF.getTokenName()) + "") %></span>
				<div id="wrapper">
					<div id="container">
						<h1>Submit a Report</h1>
						<p>Logged in as <a href="<%= StringEscapeUtils.escapeXml11(String.format("https://www.khanacademy.org/profile/%s", URLEncoder.encode(request.getAttribute("kaid").toString(), "UTF-8"))) %>"><%= StringEscapeUtils.escapeXml11(request.getAttribute("nickname") + "") %></a> (<a href="javascript:void(0)" id="log-out-link">Log out</a>)</p>
						<form id="submit">
							<div class="form-item">
								<label>Type: </label>
								<select name="type">
									<option <%= (request.getParameter("type") + "").equals("program") ? "selected" : "" %> value="program">Program</option>
									<option <%= (request.getParameter("type") + "").equals("user") ? "selected" : "" %> value="user">User</option>
								</select>
							</div>
							<div class="form-item display-flex">
								<label>Program id/user KAID: </label>
								<input type="text" name="id" value="<%= request.getParameter("id") == null ? "" : StringEscapeUtils.escapeXml11(request.getParameter("id") + "") %>" required  />
							</div>
							<div class="form-item">
								<label>How does this program/user violate the guidelines?</label>
								<textarea required name="reason" maxlength="500"></textarea>
							</div>
							<div class="form-item">
								<input type="submit" value="Report" class="buttonlink"  />
							</div>
							<p id="response"></p>
						</form>
					</div>
				</div>
				<script src="<%= request.getContextPath() %>/scripts/submit.js"></script>
			</div>
		</div>
	</body>
</html>