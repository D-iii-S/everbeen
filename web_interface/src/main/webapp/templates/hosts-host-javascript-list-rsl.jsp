<%--

  BEEN: Benchmarking Environment
  ==============================
   
  File author: David Majda

  GNU Lesser General Public License Version 2.1
  ---------------------------------------------
  Copyright (C) 2004-2006 Distributed Systems Research Group,
  Faculty of Mathematics and Physics, Charles University in Prague

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1, as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public
  License along with this library; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston,
  MA  02111-1307  USA

--%><%@
	include file="includes.jsp"
%><%@
	page import="cz.cuni.mff.been.hostmanager.*"
%><%@
	page import="cz.cuni.mff.been.hostmanager.database.*"
%><%
	String validationResult = (String)application.getAttribute("validationResult");
	HostInfoInterface[] hosts = (HostInfoInterface[])application.getAttribute("hosts");
%>{validationResult: 
<%=validationResult != null ? "\"" + Routines.javaScriptEscape(validationResult) + "\"" : "null"%>,
 hosts:
  [<% for (int i = 0; i < hosts.length; i++) { %>
  "<%=Routines.javaScriptEscape(hosts[i]
  .getHostName())%>",
  <% } %>]}