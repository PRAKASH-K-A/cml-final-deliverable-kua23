#!/usr/bin/env python3
"""
FIX Order Sender Script
Sends NewOrderSingle (MsgType=D) messages to the Java backend trading system.

Usage:
    python order_sender.py --orders 10 --delay 1.0 --mode normal
    python order_sender.py --orders 1000 --delay 0 --mode burst
"""

import socket
import time
import argparse
import sys
import random
from datetime import datetime, timezone
import struct
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from queue import Queue


# Configuration
BACKEND_HOST = 'localhost'
BACKEND_PORT = 9876
SENDER_COMP_ID = 'MINIFIX_CLIENT'
TARGET_COMP_ID = 'EXEC_SERVER'

# Test data
SYMBOLS = ['GOOG', 'MSFT', 'IBM', 'AAPL', 'AMZN']
BASE_PRICES = {
    'GOOG': 140.0,
    'MSFT': 380.0,
    'IBM': 180.0,
    'AAPL': 190.0,
    'AMZN': 170.0
}


class FIXOrderSender:
    """Manages FIX protocol connection and sends NewOrderSingle messages."""
    
    def __init__(self, num_threads=1):
        self.socket = None
        self.msg_seq_num = 1
        self.cl_ord_id_counter = 0
        self.sender_seq_num = 1
        self.num_threads = num_threads
        self.lock = threading.Lock()  # Thread-safe access to shared state
        
    def connect(self):
        """Establish connection to the Java backend."""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((BACKEND_HOST, BACKEND_PORT))
            print(f"[OK] Connected to {BACKEND_HOST}:{BACKEND_PORT}")
            
            # Step 1: Send FIX Logon message (MsgType=A) to establish session
            logon_msg = self._build_logon_message()
            if not self.send_message(logon_msg):
                print(f"[FAIL] Failed to send logon message")
                sys.exit(1)
            print(f"[OK] Logon message sent (SeqNum=1)")
            
            # Step 2: Wait for Logon response from backend
            try:
                self.socket.settimeout(5.0)
                response = self.socket.recv(4096)
                if response:
                    print(f"[OK] Received logon response ({len(response)} bytes)")
                    # Verify it's a logon response
                    if b'35=A' in response or b'35\x01A' in response:
                        print(f"[OK] Logon handshake SUCCESSFUL - Session established")
                    else:
                        print(f"[!] Response received but may not be logon: {response[:80]}")
                # Reset to blocking mode
                self.socket.settimeout(None)
                
                # Wait for QuickFIX session to fully initialize and be ready for business messages
                # This is important for protocol compliance - session must be in "logged on" state before orders
                time.sleep(0.5)
                
            except socket.timeout:
                print(f"[FAIL] Timeout waiting for logon response (5 seconds)")
                print(f"[FAIL] Backend may not be ready. Check if AppLauncher is running.")
                sys.exit(1)
            
            # Step 3: Increment msg_seq_num for future messages
            self.msg_seq_num = 2
                
        except Exception as e:
            print(f"[FAIL] Connection failed: {e}")
            sys.exit(1)
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((BACKEND_HOST, BACKEND_PORT))
            print(f"[OK] Connected to {BACKEND_HOST}:{BACKEND_PORT}")
            
            # Step 1: Send FIX Logon message (MsgType=A) to establish session
            logon_msg = self._build_logon_message()
            if not self.send_message(logon_msg):
                print(f"[FAIL] Failed to send logon message")
                sys.exit(1)
            print(f"[OK] Logon message sent (SeqNum=1)")
            
            # Step 2: Wait for Logon response from backend
            try:
                self.socket.settimeout(5.0)
                response = self.socket.recv(4096)
                if response:
                    print(f"[OK] Received logon response ({len(response)} bytes)")
                    # Verify it's a logon response (should start with Logon message)
                    if b'35=A' in response or b'35\x01A' in response:
                        print(f"[OK] Logon handshake SUCCESSFUL - Session established")
                    else:
                        print(f"[!] Response received but may not be logon: {response[:80]}")
                # Reset to blocking for normal operation
                self.socket.settimeout(None)
            except socket.timeout:
                print(f"[FAIL] Timeout waiting for logon response (5 seconds)")
                print(f"[FAIL] Backend may not be ready. Check if AppLauncher is running.")
                sys.exit(1)
            
            # Step 3: Set msg_seq_num to 2 for future messages (logon was 1)
            self.msg_seq_num = 2
                
        except Exception as e:
            print(f"[FAIL] Connection failed: {e}")
            sys.exit(1)
    
    def _build_logon_message(self):
        """
        Build FIX Logon message (MsgType=A)
        
        This must be sent first to establish a FIX session.
        """
        seq_num = 1  # Logon is always sequence 1
        fields = {
            49: SENDER_COMP_ID,   # SenderCompID (Tag 49)
            56: TARGET_COMP_ID,   # TargetCompID (Tag 56)
            98: '0',              # EncryptMethod (Tag 98 = 0 = No encryption)
            108: '60',            # HeartBtInt (Tag 108 = 60 seconds)
        }
        
        # Build using same method as order messages
        # MsgType 'A' is passed separately and will be added to the message
        msg = self._build_fix_message('A', fields, seq_num=seq_num)
        return msg
    
    def disconnect(self):
        """Close connection."""
        if self.socket:
            try:
                self.socket.close()
                print("[OK] Disconnected")
            except Exception as e:
                print(f"[ERROR] Disconnect error: {e}")
    
    def _build_fix_message(self, msg_type, fields, seq_num=None):
        """
        Build a raw FIX message with proper tag ordering.
        
        Args:
            msg_type: FIX message type (e.g., 'D' for NewOrderSingle)
            fields: Dict of tag -> value pairs
            seq_num: Optional sequence number (for thread safety)
        
        Returns:
            Raw FIX message bytes with BeginString, BodyLength, and Checksum
        """
        # Build body fields with explicit ordering (FIX requires specific tag order)
        # Header tags (should come first in body):
        # 35 (MsgType) MUST be first
        # Then: 49 (SenderCompID), 56 (TargetCompID), 34 (MsgSeqNum), 52 (SendingTime)
        
        body = ""
        
        # Tag 35 - MsgType (MUST BE FIRST)
        body += f"35={msg_type}\x01"
        
        # Standard header fields (in FIX order)
        body += f"49={SENDER_COMP_ID}\x01"
        body += f"56={TARGET_COMP_ID}\x01"
        body += f"34={str(seq_num) if seq_num is not None else str(self.msg_seq_num)}\x01"
        body += f"52={datetime.now(timezone.utc).strftime('%Y%m%d-%H:%M:%S')}\x01"
        
        # Add order-specific fields in numeric order
        for tag in sorted(fields.keys()):
            body += f"{tag}={fields[tag]}\x01"
        
        # Build the full message: BeginString + BodyLength + Body
        begin_string = "8=FIX.4.4\x01"  # Tag 8 with value FIX.4.4
        body_length = f"9={len(body)}\x01"  # Tag 9 = length of body (excluding BeginString, BodyLength, and Checksum)
        
        # Combine header + body (for checksum calculation)
        message = begin_string + body_length + body
        
        # Calculate checksum (sum of all bytes modulo 256)
        checksum = 0
        for byte in message.encode('ascii'):
            checksum += byte
        checksum = checksum % 256
        
        # Format checksum as 3-digit number with leading zeros (Tag 10 = Checksum field)
        checksum_field = f"10={checksum:03d}\x01"
        
        # Final message: message + checksum
        final_message = (message + checksum_field).encode('ascii')
        
        return final_message
    
    def _get_next_seq_num(self):
        """Get next message sequence number in a thread-safe manner."""
        with self.lock:
            seq_num = self.msg_seq_num
            self.msg_seq_num += 1
            return seq_num
    
    def _get_next_cl_ord_id(self):
        """Get next client order ID in a thread-safe manner."""
        with self.lock:
            self.cl_ord_id_counter += 1
            return f"ORD_{int(time.time() * 1000)}_{self.cl_ord_id_counter}"
    
    def create_new_order_single(self, symbol, side, qty, price):
        """
        Create a FIX NewOrderSingle message (MsgType=D).
        
        Args:
            symbol: Trading symbol (e.g., 'GOOG')
            side: '1' for Buy, '2' for Sell
            qty: Order quantity
            price: Limit price
        """
        # Get thread-safe IDs
        seq_num = self._get_next_seq_num()
        cl_ord_id = self._get_next_cl_ord_id()
        
        fields = {
            11: cl_ord_id,          # ClOrdID
            55: symbol,             # Symbol
            54: side,               # Side (1=Buy, 2=Sell)
            38: str(int(qty)),      # OrderQty
            44: f"{price:.2f}",     # Price
            40: '2',                # OrdType (2=Limit)
            59: '0',                # TimeInForce (0=Day)
            60: datetime.now(timezone.utc).strftime('%Y%m%d-%H:%M:%S'),  # TransactTime
        }
        
        msg = self._build_fix_message('D', fields, seq_num=seq_num)
        return msg, cl_ord_id
    
    def send_message(self, msg):
        """Send a FIX message to the backend."""
        try:
            with self.lock:  # Thread-safe socket access
                self.socket.sendall(msg)
            return True
        except Exception as e:
            print(f"[ERROR] Send error: {e}")
            return False
    
    def send_single_order(self, order_index):
        """
        Send a single order (used by thread pool).
        
        Args:
            order_index: Index of the order (for display)
        
        Returns:
            Tuple of (success: bool, side_str: str, qty: int, symbol: str, price: float, cl_ord_id: str)
        """
        try:
            # Generate order
            symbol, side, qty, price = self.generate_order()
            msg, cl_ord_id = self.create_new_order_single(symbol, side, qty, price)
            
            # Send
            if self.send_message(msg):
                side_str = "BUY " if side == '1' else "SELL"
                return (True, order_index, side_str, qty, symbol, price, cl_ord_id)
            else:
                return (False, order_index, None, None, None, None, None)
        except Exception as e:
            print(f"[ERROR] Error in order {order_index}: {e}")
            return (False, order_index, None, None, None, None, None)
    
    def generate_order(self):
        """Generate a random order with realistic parameters."""
        symbol = random.choice(SYMBOLS)
        side = str(random.randint(1, 2))  # 1=Buy, 2=Sell
        qty = random.choice([50, 100, 150, 200, 500])
        
        # Price with slight variation
        base_price = BASE_PRICES[symbol]
        price_variance = random.uniform(-5, 5)
        price = round(base_price + price_variance, 2)
        
        return symbol, side, qty, price
    
    def send_orders(self, num_orders, delay, mode='normal', num_threads=1):
        """
        Send multiple orders to the backend.
        
        Args:
            num_orders: Number of orders to send
            delay: Delay between orders (seconds). 0 = burst mode
            mode: 'normal' or 'burst'
            num_threads: Number of worker threads (1 = single-threaded, >1 = multi-threaded)
        """
        print(f"\n{'='*60}")
        print(f"Order Sending Configuration")
        print(f"{'='*60}")
        print(f"Number of Orders: {num_orders}")
        print(f"Delay Mode: {mode}")
        print(f"Worker Threads: {num_threads}")
        if mode == 'normal':
            print(f"Delay Between Orders: {delay} second(s)")
        else:
            print(f"Sending Mode: BURST (minimal delay)")
        print(f"{'='*60}\n")
        
        sent_count = 0
        failed_count = 0
        start_time = time.time()
        
        # Single-threaded mode (original behavior)
        if num_threads == 1:
            for i in range(num_orders):
                try:
                    # Generate order
                    symbol, side, qty, price = self.generate_order()
                    msg, cl_ord_id = self.create_new_order_single(symbol, side, qty, price)
                    
                    # Send
                    if self.send_message(msg):
                        side_str = "BUY " if side == '1' else "SELL"
                        print(f"[{i+1:4d}] {side_str} {qty:5d} {symbol} @ ${price:8.2f} | ClOrdID: {cl_ord_id}")
                        sent_count += 1
                    else:
                        failed_count += 1
                    
                    # Delay between orders (unless burst mode)
                    if mode == 'normal' and delay > 0 and i < num_orders - 1:
                        time.sleep(delay)
                    elif mode == 'burst' and i < num_orders - 1:
                        # Minimal delay to avoid overwhelming
                        time.sleep(0.001)
                        
                except Exception as e:
                    print(f"[ERROR] Error generating order {i+1}: {e}")
                    failed_count += 1
        else:
            # Multi-threaded mode
            with ThreadPoolExecutor(max_workers=num_threads) as executor:
                # Submit all tasks
                futures = {}
                for i in range(num_orders):
                    future = executor.submit(self.send_single_order, i + 1)
                    futures[future] = i + 1
                
                # Process results as they complete
                results = []
                for future in as_completed(futures):
                    result = future.result()
                    results.append(result)
                    
                    if result[0]:  # Success
                        success, order_idx, side_str, qty, symbol, price, cl_ord_id = result
                        print(f"[{order_idx:4d}] {side_str} {qty:5d} {symbol} @ ${price:8.2f} | ClOrdID: {cl_ord_id}")
                        sent_count += 1
                    else:
                        failed_count += 1
        
        elapsed = time.time() - start_time
        
        print(f"\n{'='*60}")
        print(f"Summary")
        print(f"{'='*60}")
        print(f"Total Sent:    {sent_count}")
        print(f"Failed:        {failed_count}")
        print(f"Total Time:    {elapsed:.2f} seconds")
        if elapsed > 0:
            print(f"Throughput:    {sent_count/elapsed:.2f} orders/sec")
        print(f"{'='*60}\n")


def get_user_input():
    """Get order configuration from user interactively."""
    print(f"\n{'='*60}")
    print("FIX Order Sender - Interactive Configuration")
    print(f"{'='*60}\n")
    
    # Get number of orders
    while True:
        try:
            num_orders = int(input("Enter number of orders to send (1-100000) [default: 10]: ").strip() or "10")
            if 1 <= num_orders <= 100000:
                break
            else:
                print("[!] Please enter a number between 1 and 100000")
        except ValueError:
            print("[!] Invalid input. Please enter a valid number")
    
    # Get delay
    while True:
        try:
            delay = float(input("Enter delay between orders in seconds (0-10) [default: 1.0]: ").strip() or "1.0")
            if 0 <= delay <= 10:
                break
            else:
                print("[!] Please enter a value between 0 and 10")
        except ValueError:
            print("[!] Invalid input. Please enter a valid number")
    
    # Get mode
    print("\nSelect sending mode:")
    print("  1. Normal   (respects delay value)")
    print("  2. Burst    (send as fast as possible)")
    
    while True:
        mode_choice = input("Enter choice (1 or 2) [default: 1]: ").strip() or "1"
        if mode_choice == "1":
            mode = "normal"
            break
        elif mode_choice == "2":
            mode = "burst"
            break
        else:
            print("[!] Invalid choice. Please enter 1 or 2")
    
    # Get number of threads
    print("\nThreading options:")
    print("  1. Single-threaded (1 thread)")
    print("  2. Multi-threaded (specify number)")
    
    while True:
        thread_choice = input("Enter choice (1 or 2) [default: 1]: ").strip() or "1"
        if thread_choice == "1":
            num_threads = 1
            break
        elif thread_choice == "2":
            try:
                num_threads = int(input("Enter number of worker threads (2-64) [default: 4]: ").strip() or "4")
                if 2 <= num_threads <= 64:
                    break
                else:
                    print("[!] Please enter a number between 2 and 64")
            except ValueError:
                print("[!] Invalid input. Please enter a valid number")
        else:
            print("[!] Invalid choice. Please enter 1 or 2")
    
    # Auto-correct mode if delay is 0 in normal mode
    if mode == "normal" and delay == 0:
        print("[*] Delay is 0, automatically switching to burst mode")
        mode = "burst"
    
    return num_orders, delay, mode, num_threads


def main():
    """Parse arguments and run the order sender."""
    parser = argparse.ArgumentParser(
        description='Send FIX NewOrderSingle messages to Java trading backend',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Usage Modes:
  1. Interactive (no arguments):
     python order_sender.py
     
  2. Command-line arguments:
     python order_sender.py --orders 10 --delay 1.0 --mode normal
     python order_sender.py --orders 1000 --mode burst
     python order_sender.py --orders 500 --delay 0.01 --threads 8

Multithreading Examples:
     python order_sender.py --orders 10000 --mode burst --threads 4
     python order_sender.py --orders 50000 --mode burst --threads 16

Use -h or --help to see all options:
     python order_sender.py -h
        """
    )
    
    parser.add_argument(
        '--orders',
        type=int,
        default=None,
        help='Number of orders to send'
    )
    parser.add_argument(
        '--delay',
        type=float,
        default=None,
        help='Delay between orders in seconds (0 for burst)'
    )
    parser.add_argument(
        '--mode',
        choices=['normal', 'burst'],
        default=None,
        help='Send mode: normal (with delay) or burst (rapid fire)'
    )
    parser.add_argument(
        '--threads',
        type=int,
        default=None,
        help='Number of worker threads for parallel sending (1-64, default: 1)'
    )
    
    args = parser.parse_args()
    
    # Determine if using CLI arguments or interactive mode
    if args.orders is not None or args.delay is not None or args.mode is not None or args.threads is not None:
        # CLI mode - use provided arguments with defaults
        num_orders = args.orders or 10
        delay = args.delay if args.delay is not None else 1.0
        mode = args.mode or 'normal'
        num_threads = args.threads or 1
    else:
        # Interactive mode
        num_orders, delay, mode, num_threads = get_user_input()
    
    # Validate arguments
    if num_orders < 1:
        print("[ERROR] Number of orders must be >= 1")
        sys.exit(1)
    
    if delay < 0:
        print("[ERROR] Delay cannot be negative")
        sys.exit(1)
    
    if num_threads < 1 or num_threads > 64:
        print("[ERROR] Number of threads must be between 1 and 64")
        sys.exit(1)
    
    # Auto-set mode based on delay
    if mode == 'normal' and delay == 0:
        print("[*] Delay is 0, switching to burst mode")
        mode = 'burst'
    
    # Run sender
    sender = FIXOrderSender(num_threads=num_threads)
    try:
        sender.connect()
        sender.send_orders(num_orders, delay, mode, num_threads=num_threads)
    except KeyboardInterrupt:
        print("\n[!] Interrupted by user")
    except Exception as e:
        print(f"[ERROR] Fatal error: {e}")
        import traceback
        traceback.print_exc()
    finally:
        sender.disconnect()


if __name__ == '__main__':
    main()
