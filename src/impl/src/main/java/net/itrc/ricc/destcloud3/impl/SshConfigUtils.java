package net.itrc.ricc.destcloud3.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SshConfigUtils {

    private final static Logger LOG = LoggerFactory.getLogger(SshConfigUtils.class);
	private final static long WAIT_TIME = 10000;
	private final static int SSH_TIMEOUT = 10000;

	public static SshResult runVyattaSetCommand(String address, String username,
			String password, String command) {
		List<String> list = new ArrayList<String>();
		list.add(command);
		return runVyattaSetCommandList(address, username, password, list);
	}

	public static SshResult runVyattaSetCommandList(String address, String username,
			String password, List<String> commandList) {
		SshResult sshResult = new SshResult(true, "success");

		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(username, address, 22);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect(SSH_TIMEOUT);

			Channel channel = session.openChannel("shell");
			InputStream in = channel.getInputStream();
			OutputStream out = channel.getOutputStream();
			channel.connect(SSH_TIMEOUT);

			List<String> commands = new ArrayList<String>();
			commands.add("configure\r\n");
			for (int i = 0; i < commandList.size(); i++) {
				String tmp = commandList.get(i);
				tmp = tmp + "\r\n";
				commands.add(tmp);
			}
			commands.add("commit\r\n");
			commands.add("exit\r\n");
			byte[] tmp = new byte[2048];
			boolean invalid = false;
			for (int i = 0; i < commands.size() + 1; i++) {
				if (i > 0) {
					out.write(commands.get(i - 1).getBytes());
					out.flush();
				}

				boolean breakable = false;
				long s_time = System.currentTimeMillis();
				while (System.currentTimeMillis() - s_time <= WAIT_TIME) {
					while (in.available() > 0) {
						int x = in.read(tmp, 0, 2048);
						if (x < 0) {
							breakable = true;
							break;
						}
						String recvStr = new String(tmp, 0, x);
						LOG.error("recv => " + recvStr);
						if (i == 0) {
							breakable = true;
							break;
						} else {
							if (recvStr.contains("failed")
									|| recvStr.contains("Invalid")) {
								sshResult.setResult(false);
								sshResult.setMessage(recvStr);
								invalid = true;
								break;
							} else if (recvStr.contains("#")
									|| recvStr.contains("$")) {
								breakable = true;
								break;
							}
						}
					}
					if (breakable || invalid) {
						break;
					}
					if (channel.isClosed()) {
						break;
					}
					try {
						Thread.sleep(100);
					} catch (Exception ee) {
					}
				}
				if (invalid) {
					break;
				}
			}

			channel.disconnect();
			session.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
			sshResult.setResult(false);
			sshResult.setMessage(e.getMessage());
		}

		return sshResult;
	}

	public static SshResult runVyattaShowCommand(String address, String username,
			String password, String showCommand) {
		SshResult sshResult = new SshResult(true, "success");
		String command = showCommand + " | no-more\r\n";

		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(username, address, 22);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect(SSH_TIMEOUT);

			Channel channel = session.openChannel("shell");
			InputStream in = channel.getInputStream();
			OutputStream out = channel.getOutputStream();
			channel.connect(SSH_TIMEOUT);

			String resultStr = "";
			byte[] tmp = new byte[2048];
			boolean endLoop = false;
			boolean found1stPrompt = false;
			for (int i = 0;; i++) {
				if (i != 0) {
					if (i == 1) {
						out.write(command.getBytes());
						out.flush();
					//} else {
					//	out.write(" ".getBytes());
					//	out.flush();
					}
				}

				boolean breakable = false;
				long s_time = System.currentTimeMillis();
				while (System.currentTimeMillis() - s_time <= WAIT_TIME) {
					while (in.available() > 0) {
						int x = in.read(tmp, 0, 2048);
						if (x < 0) {
							breakable = true;
							break;
						}
						String recvStr = new String(tmp, 0, x);
						if (i < 1) {
							resultStr = "";
							breakable = true;
							break;
						} else {
						    LOG.error("recv => " + recvStr);
							if (recvStr.contains("Invalid")) {
								sshResult.setResult(false);
								sshResult.setMessage(recvStr);
								endLoop = true;
								break;
							}
							resultStr += recvStr;
							if (!found1stPrompt) {
							    int index = resultStr.indexOf('$');
							    if (index != -1) {
							        found1stPrompt = true;
							    }
							}
							if (found1stPrompt) {
							    int index = resultStr.indexOf('$');
							    if (index != -1) {
							        String tmpStr = resultStr.substring(index + 1);
							        index = tmpStr.indexOf('$');
							        if (index != -1) {
							            sshResult.setMessage(resultStr);
							            endLoop = true;
							        }
							    }
							}
							
							/*
							if (i >= 1) {
								int index = recvStr.indexOf(command);
								if (index != -1) {
									recvStr = recvStr.substring(index
											+ command.length(),
											recvStr.length());
								}
							}
							resultStr += recvStr;
							if (recvStr.contains("$")) {
								int index = resultStr.indexOf(username + "@");
								if (index != -1) {
									resultStr = resultStr.substring(0, index);
								}
								char esc = (char) 27;
								char bel = (char) 7;
								String startStr = esc + "\\[\\?1h" + esc + "=";
								String string1 = ":" + esc + "\\[K";
								String string2 = esc + "\\[K";
								String breakLineStr = esc + "\\[m";
								String endStr = esc + "\\[\\?1l" + esc + ">";
								resultStr = resultStr.replaceAll(startStr, "");
								resultStr = resultStr.replaceAll(endStr, "");
								resultStr = resultStr.replaceAll(string1, "");
								resultStr = resultStr.replaceAll(string2, "");
								resultStr = resultStr.replaceAll(breakLineStr, "");
								resultStr = resultStr.replaceAll(bel + "", "");
								sshResult.setMessage(resultStr);
								endLoop = true;
								break;
							} else {
								breakable = true;
							} */
						}
					}
					if (breakable || endLoop) {
						break;
					}
					if (channel.isClosed()) {
						break;
					}
					try {
						Thread.sleep(100);
					} catch (Exception ee) {
					}
				}
				if (endLoop) {
					break;
				}
			}

			channel.disconnect();
			session.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
			sshResult.setResult(false);
			sshResult.setMessage(e.getMessage());
		}

		return sshResult;
	}

	public static SshResult runJuniperSetCommand(String address, String username,
			String password, String command) {
		List<String> list = new ArrayList<String>();
		list.add(command);
		return runJuniperSetCommandList(address, username, password, list);
	}

	public static SshResult runJuniperSetCommandList(String address, String username,
			String password, List<String> commandList) {
		SshResult sshResult = new SshResult(true, "success");

		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(username, address, 22);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect(SSH_TIMEOUT);

            Channel channel = session.openChannel("shell");
            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();
            channel.connect(SSH_TIMEOUT);

            List<String> commands = new ArrayList<String>();
            // commands.add("cli\n");
            commands.add("configure\n");
            for (int i = 0; i < commandList.size(); i++) {
                String tmp = commandList.get(i);
                tmp = tmp + "\n";
                commands.add(tmp);
            }
            commands.add("commit\n");
            commands.add("exit\n");
            commands.add("exit\n");
            // commands.add("exit\n");
            byte[] tmp = new byte[2048];
            boolean invalid = false;
            for (int i = -1; i < commands.size(); i++) {
                if (i >= 0 ) {
                    out.write(commands.get(i).getBytes());
                    out.flush();
                }

                boolean breakable = false;
                long s_time = System.currentTimeMillis();
                while (System.currentTimeMillis() - s_time <= WAIT_TIME) {
                    while (in.available() > 0) {
                        int x = in.read(tmp, 0, 2048);
                        if (x < 0) {
                            breakable = true;
                            break;
                        }
                        String recvStr = new String(tmp, 0, x);
                        LOG.error("recv => " + recvStr);
                        if (recvStr.contains("error")) {
                        	sshResult.setResult(false);
                        	sshResult.setMessage(recvStr);
                            invalid = true;
                            break;
                        } else if (recvStr.contains("%") || recvStr.contains(">") || recvStr.contains("#")) {
                            breakable = true;
                            break;
                        }
                    }
                    if (breakable || invalid) {
                        break;
                    }
                    if (channel.isClosed()) {
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (Exception ee) {
                    }
                }
                if (invalid) {
                    break;
                }
            }

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            sshResult.setResult(false);
            sshResult.setMessage(e.getMessage());
        }

		return sshResult;
	}

	public static SshResult runJuniperShowCommand(String address, String username,
			String password, String showCommand) {
		SshResult sshResult = new SshResult(true, "success");

		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(username, address, 22);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect(SSH_TIMEOUT);

            Channel channel = session.openChannel("shell");
            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();
            channel.connect(SSH_TIMEOUT);

            List<String> commands = new ArrayList<String>();
            String resultStr = "";
            // commands.add("cli\n");
            commands.add("set cli screen-width 1024\n");
            commands.add(showCommand + "| no-more\n");
            byte[] tmp = new byte[2048];
            boolean invalid = false;
            for (int i = -1; i < commands.size(); i++) {
                if (i >= 0 ) {
                    out.write(commands.get(i).getBytes());
                    out.flush();
                }

                boolean breakable = false;
                long s_time = System.currentTimeMillis();
                while (System.currentTimeMillis() - s_time <= WAIT_TIME) {
                    while (in.available() > 0) {
                        int x = in.read(tmp, 0, 2048);
                        if (x < 0) {
                            breakable = true;
                            break;
                        }
                        String recvStr = new String(tmp, 0, x);
                        LOG.error("recv => " + recvStr);
                        resultStr = resultStr + recvStr;
                        if (recvStr.contains("Invalid")) {
                        	sshResult.setResult(false);
                            sshResult.setMessage(recvStr);
                            invalid = true;
                            break;
                        } else if (recvStr.contains("%")) {
                            resultStr = "";
                            breakable = true;
                            break;
                        } else if (recvStr.contains("#") || recvStr.contains(">")) {
                            breakable = true;
                            sshResult.setMessage(resultStr);
                            break;
                        }
                    }
                    if (breakable || invalid) {
                        break;
                    }
                    if (channel.isClosed()) {
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (Exception ee) {
                    }
                }
                if (invalid) {
                    break;
                }
            }

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            sshResult.setResult(false);
            sshResult.setMessage(e.getMessage());
        }

        return sshResult;
	}

}
