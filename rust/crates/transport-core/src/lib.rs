use pnet::packet::ipv4::Ipv4Packet;
use pnet::packet::ipv6::Ipv6Packet;
use pnet::packet::tcp::TcpPacket;
use pnet::packet::Packet;
use std::collections::BTreeMap;
use std::net::IpAddr;

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Hash, serde::Serialize)]
pub struct FiveTuple {
    pub source_ip: IpAddr,
    pub dest_ip: IpAddr,
    pub source_port: u16,
    pub dest_port: u16,
    pub is_tcp: bool,
}

#[derive(Debug)]
pub struct TcpSegment {
    pub seq: u32,
    pub payload: Vec<u8>,
}

pub struct TcpStreamReassembler {
    pub expected_seq: u32,
    pub segments: BTreeMap<u32, Vec<u8>>,
    pub is_established: bool,
}

impl TcpStreamReassembler {
    pub fn new(initial_seq: u32) -> Self {
        Self {
            expected_seq: initial_seq,
            segments: BTreeMap::new(),
            is_established: false,
        }
    }

    pub fn insert(&mut self, seq: u32, payload: &[u8]) -> Option<Vec<u8>> {
        if payload.is_empty() {
            return None;
        }

        // Ignore segments entirely before the expected sequence
        let end_seq = seq.wrapping_add(payload.len() as u32);
        if seq < self.expected_seq && end_seq <= self.expected_seq {
            return None;
        }

        self.segments.insert(seq, payload.to_vec());

        // Reassemble contiguous blocks
        let mut contiguous_data = Vec::new();
        while let Some((&seq_num, payload_vec)) = self.segments.first_key_value() {
            if seq_num == self.expected_seq {
                contiguous_data.extend_from_slice(payload_vec);
                self.expected_seq = self.expected_seq.wrapping_add(payload_vec.len() as u32);
                self.segments.pop_first();
            } else if seq_num < self.expected_seq {
                // Duplicate or overlapping packet; drop it
                self.segments.pop_first();
            } else {
                break;
            }
        }

        if contiguous_data.is_empty() {
            None
        } else {
            Some(contiguous_data)
        }
    }
}

pub struct TransportEngine {
    pub active_connections: BTreeMap<FiveTuple, TcpStreamReassembler>,
}

impl TransportEngine {
    pub fn new() -> Self {
        Self {
            active_connections: BTreeMap::new(),
        }
    }

    pub fn process_ip_packet(&mut self, ip_packet_bytes: &[u8]) -> Option<(FiveTuple, Vec<u8>)> {
        if ip_packet_bytes.is_empty() {
            return None;
        }

        let version = ip_packet_bytes[0] >> 4;
        let (src_ip, dest_ip, next_proto, payload_bytes) = if version == 4 {
            if let Some(ipv4) = Ipv4Packet::new(ip_packet_bytes) {
                (
                    IpAddr::V4(ipv4.get_source()),
                    IpAddr::V4(ipv4.get_destination()),
                    ipv4.get_next_level_protocol(),
                    ipv4.payload().to_vec(),
                )
            } else {
                return None;
            }
        } else if version == 6 {
            if let Some(ipv6) = Ipv6Packet::new(ip_packet_bytes) {
                (
                    IpAddr::V6(ipv6.get_source()),
                    IpAddr::V6(ipv6.get_destination()),
                    ipv6.get_next_header(),
                    ipv6.payload().to_vec(),
                )
            } else {
                return None;
            }
        } else {
            return None;
        };

        if next_proto == pnet::packet::ip::IpNextHeaderProtocols::Tcp {
            if let Some(tcp) = TcpPacket::new(&payload_bytes) {
                let five_tuple = FiveTuple {
                    source_ip: src_ip,
                    dest_ip,
                    source_port: tcp.get_source(),
                    dest_port: tcp.get_destination(),
                    is_tcp: true,
                };

                let reassembler = self
                    .active_connections
                    .entry(five_tuple.clone())
                    .or_insert_with(|| TcpStreamReassembler::new(tcp.get_sequence()));

                if let Some(assembled_payload) = reassembler.insert(tcp.get_sequence(), tcp.payload()) {
                    return Some((five_tuple, assembled_payload));
                }
            }
        }

        None
    }
}
