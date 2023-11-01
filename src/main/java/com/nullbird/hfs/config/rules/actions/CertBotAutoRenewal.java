package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.TomcatSslConfig;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.apache.hc.core5.http.ContentType;

import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This action uses the installed binary of <code>certbot</code> to renew certificates for your domains.
 * It should be used in conjunction with the `All` rule as it needs to match certbot's servers requests
 * to validate the domain ownership.
 */
public class CertBotAutoRenewal implements RuleAction {
  private final static Logger LOGGER = Logger.getLogger(CertBotAutoRenewal.class.getSimpleName());
  public final static String WEBROOT_PATH_PATTERN = "$WEBROOT_PATH";

  private transient TomcatSslConfig sslConfig;
  private transient Set<String> domainsSet;
  private transient Boolean run = null;

  private transient RenewalProcess renewal;

  /**
   * An array specifying the domain names this action should get https certs for.
   */
  protected List<String> domains;

  /**
   * Holds the full name of the certbot executable file on your system, for example <code>/usr/bin/certbot</code>.
   */
  protected String executable;

  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) throws Exception {
    var r = renewal;
    if (r!=null) {
      String path = request.getPath();
      File target = r.rootFolder;
      for (var element : path.split("/")) {
        if (element.equals("..")) return;
        target = new File(target, element);
      }
      if (target.exists() && target.isFile() && target.getCanonicalPath().startsWith(r.rootFolder.getCanonicalPath())) {
        LOGGER.info("Serving file " + target.getAbsolutePath());
        response.respond(200, ContentType.TEXT_PLAIN, Files.readString(target.toPath(), StandardCharsets.UTF_8));
      }
    }
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    sslConfig = config.getTomcatConfig().getSslConfig();
    if (domains==null || domains.size()<1) throw new ConfigurationException("CertBotAutoRenewal action definition must include a non empty 'domains' attribute");
    if (executable==null || executable.length()<7) throw new ConfigurationException("CertBotAutoRenewal action definition must include a non empty 'executable' attribute");
    domainsSet = new HashSet<>(domains);
    new Thread(this::run).start();
  }

  @Override
  public void stop() {
    Boolean oldValue = run;
    run = false;
    synchronized (oldValue) {
      oldValue.notifyAll();
    }
  }

  public void run() {
    if (run == null) run = true;
    while (run) {
      try {
        synchronized (run) {
          run.wait(Duration.ofSeconds(1).toMillis());
        }
        if (!run) break;
        checkCerts();
        synchronized (run) {
          run.wait(Duration.ofHours(23).toMillis());
        }
      } catch (Exception e) {
        LOGGER.warning("Wait was interrupted");
      }
    }
  }

  private synchronized void checkCerts() {
    try {
      File certFile = new File(sslConfig.getCertificateFile());
      if (!certFile.exists()) {
        LOGGER.log(Level.INFO, "Certificate could not be found, launching certbot");
        renewCert();
      } else {
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        FileInputStream is = new FileInputStream(certFile);
        X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
        is.close();

        double days = getDaysLeft(cer);
        Set<String>domains = getDomains(cer);
        LOGGER.log(Level.INFO, String.format("Certificate still has %.2f days left",days));
        if (days < 30) {
          LOGGER.log(Level.INFO, "Renewing - date too close");
          renewCert();
        } else if (!domains.equals(domainsSet)) {
          LOGGER.log(Level.INFO, "Renewing - domain names mismatch");
          renewCert();
        }
      }
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Trying to read the current certificate", e);
    }
  }

  private Set<String> getDomains(X509Certificate cer) {
    Set<String> res = new HashSet<>();
    try {
      res.add(cer.getSubjectX500Principal().getName(X500Principal.CANONICAL).substring(3));
      LOGGER.info("CERT domain name:" + res.stream().collect(Collectors.joining()));
      if (cer.getSubjectAlternativeNames()!=null) {
        res.addAll(cer.getSubjectAlternativeNames().stream().filter(l->l.get(0).equals(2)).map(l -> String.valueOf(l.get(1))).collect(Collectors.toSet()));
        LOGGER.info("CERT all domain name:" + res.stream().collect(Collectors.joining(",")));
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Getting domains", e);
    }
    return res;
  }

  private synchronized void renewCert() throws Exception {
    try {
      File tempDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
      renewal = new RenewalProcess(tempDir);
      String[] args = new String[]{
          executable,
          "certonly",
          "--force-renewal",
          "--expand",
          "-n",
          "--webroot",
          "--webroot-path",
          tempDir.getCanonicalPath(),
          "-d",
          domains.stream().collect(Collectors.joining(","))
      };

      ProcessBuilder ps = new ProcessBuilder(args);
      ps.redirectErrorStream(true);
      Process pr = ps.start();
      BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
      String line;
      while ((line = in.readLine()) != null) {
        LOGGER.info(line);
        line = line.trim();
        if (line.startsWith("/etc/letsencrypt/") && line.endsWith("/fullchain.pem")) {
          copyCerts(line);
        }
      }
      pr.waitFor();
      in.close();
    } finally {
      try {
        renewal.rootFolder.delete();
      } catch (Exception e) {
        LOGGER.warning("Could not remove " + renewal.rootFolder.getAbsolutePath());
      } finally {
        this.renewal = null;
      }
    }
  }

  private void copyCerts(String line) throws IOException {
    File source = new File(line).getParentFile();

    File cert = new File(sslConfig.getCertificateFile());
    copyFile(source, cert.getParentFile(), cert.getName());
    File chain = new File(sslConfig.getCertificateChainFile());
    copyFile(source, chain.getParentFile(), chain.getName());
    File key = new File(sslConfig.getCertificateKeyFile());
    copyFile(source, key.getParentFile(), key.getName());
    LOGGER.info("Certificate files moved to their target place.");
  }

  private void copyFile(File source, File dest, String filename) throws IOException {
    File to = new File(dest, filename);
    Files.copy(new File(source, filename).toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    to.setReadable(false, false);
    to.setWritable(false, false);
    to.setReadable(true, true);
    to.setWritable(true, true);
  }

  private double getDaysLeft(X509Certificate cer) {
    return (cer.getNotAfter().getTime() - System.currentTimeMillis()) / (1.0 * TimeUnit.DAYS.toMillis(1));
  }
}

class RenewalProcess {
  File rootFolder;

  public RenewalProcess(File rootFolder) {
    this.rootFolder = rootFolder;
  }
}
