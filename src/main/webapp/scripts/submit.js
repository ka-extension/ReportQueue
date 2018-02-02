
const ftok = document.getElementById("ftok").textContent,
	contextPath = document.getElementById("context-path").textContent,
	form = document.getElementById("submit"),
	logOutLink = document.getElementById("log-out-link");

const programIdRegex = /^\d{10,30}$/i,
	kaidRegex = /^kaid_\d{20,40}$/i;

function parseQueryString(queryString) {
	var str = queryString[0] == "?" ? queryString.substr(1) : queryString;
	let items = {};
	let itemsArray = str.split("&");
	for(let i = 0; i < itemsArray.length; i++) {
		let item = itemsArray[i].split("=");
		items[decodeURIComponent(item[0])] = decodeURIComponent(item[1]);
	}
	return items;
  }

logOutLink.addEventListener("click", function() {
	let x = new XMLHttpRequest();
	x.open("DELETE", contextPath + "/logout");
	x.setRequestHeader("x-ftok", ftok);
	x.addEventListener("load", function() { window.location.reload(); });
	x.send();
});

form.addEventListener("submit", function(e) {
	e.preventDefault();	
	
	const responseArea = document.getElementById("response");
	const types = ["program", "user"], 
		typeRegexes = [programIdRegex, kaidRegex];
	
	const typeEl = form.querySelector("[name=type]"),
		idEl = form.querySelector("[name=id]"),
		reasonEl = form.querySelector("[name=reason]"),
		submit = form.querySelector("[type=submit]");
	
	const type = typeEl.value, id = idEl.value, reason = reasonEl.value.trim();
	
	typeEl.disabled = id.disabled = reason.disabled = submit.disabled = true;
	
	responseArea.textContent = "";
	
	if(reason.length == 0) {
		responseArea.textContent = "Please describe how this program/user violates the guidelines";
		responseArea.style.color = "#FF0000";
		typeEl.disabled = id.disabled = reason.disabled = submit.disabled = false;
		return;
	} 
	
	if(!types.includes(type)) {
		responseArea.textContent = "Invalid type";
		responseArea.style.color = "#FF0000";
		typeEl.disabled = id.disabled = reason.disabled = submit.disabled = false;
		return;
	}
	
	if(!typeRegexes[types.indexOf(type)].test(id)) {
		responseArea.textContent = "Invalid id";
		responseArea.style.color = "#FF0000";
		typeEl.disabled = id.disabled = reason.disabled = submit.disabled = false;
		return;
	}
	
	let x = new XMLHttpRequest();
	x.open("POST", contextPath + "/report/" + type);
	x.setRequestHeader("x-ftok", ftok);
	x.setRequestHeader("Content-type", "application/json");
	x.responseType = "json";
	
	function response() {
		responseArea.style.color = x.response && x.response.success ? "#007F00" : "#FF0000";
		responseArea.textContent = x.response ? (x.response.message || "Error") : "Error";
		
		var callback = parseQueryString(window.location.search).callback;
		
		if(x.response && x.response.success) {
			window.setTimeout(function() { window.location.href = /^https:\/\/(?:(?:www|[a-z]{2})\.)?khanacademy\.org.*$/i.test(callback) ? callback : (contextPath || "/"); }, 1500);
		} else {
			typeEl.disabled = id.disabled = reason.disabled = submit.disabled = false;
		}
	}
	
	x.addEventListener("load", response);
	x.addEventListener("error", response);
	x.send(JSON.stringify({
		id: id,
		reason: reason
	}));
});