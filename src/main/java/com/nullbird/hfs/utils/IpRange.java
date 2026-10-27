package com.nullbird.hfs.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * An IP address (<code>10.0.0.1</code>, <code>::1</code>) or a CIDR range (<code>10.0.0.0/8</code>,
 * <code>2001:db8::/32</code>). Only IP literals are accepted: host names are never resolved.
 */
public class IpRange {
  private static final Pattern IPV4 = Pattern.compile("(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");
  private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");

  private final byte[] network;
  private final int prefixLength;

  private IpRange(byte[] network, int prefixLength) {
    this.network = network;
    this.prefixLength = prefixLength;
  }

  /**
   * @throws IllegalArgumentException if <code>value</code> is not an IP literal, optionally followed by a valid
   * <code>/prefix</code>.
   */
  public static IpRange parse(String value) {
    String address = value.trim();
    int slash = address.indexOf('/');
    String prefix = null;
    if (slash >= 0) {
      prefix = address.substring(slash + 1);
      address = address.substring(0, slash);
    }
    byte[] bytes = parseAddress(address);
    if (bytes == null) throw new IllegalArgumentException("Not an IP address: " + value);
    int prefixLength = bytes.length * 8;
    if (prefix != null) {
      if (!prefix.matches("\\d{1,3}")) throw new IllegalArgumentException("Invalid prefix length: " + value);
      prefixLength = Integer.parseInt(prefix);
      if (prefixLength > bytes.length * 8) throw new IllegalArgumentException("Invalid prefix length: " + value);
    }
    return new IpRange(bytes, prefixLength);
  }

  /**
   * @return true if <code>address</code> is an IP literal within this range. IPv4 addresses never match IPv6
   * ranges and conversely, except IPv4-mapped IPv6 addresses (<code>::ffff:10.0.0.1</code>), which are IPv4.
   */
  public boolean contains(String address) {
    byte[] bytes = address == null ? null : parseAddress(address.trim());
    if (bytes == null || bytes.length != network.length) return false;
    for (int bit = 0; bit < prefixLength; bit++) {
      int mask = 0x80 >> (bit % 8);
      if ((bytes[bit / 8] & mask) != (network[bit / 8] & mask)) return false;
    }
    return true;
  }

  /**
   * @return The 4 or 16 bytes of the address, or null if it is not an IP literal.
   */
  private static byte[] parseAddress(String address) {
    // InetAddress.getByName would resolve host names: only hand it strings that can only be literals
    if (!IPV4.matcher(address).matches() && !IPV6.matcher(address).matches()) return null;
    try {
      return InetAddress.getByName(address).getAddress();
    } catch (UnknownHostException e) {
      return null;
    }
  }
}
